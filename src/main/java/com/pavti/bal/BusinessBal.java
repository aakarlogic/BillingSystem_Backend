package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.PasswordHelper;
import com.pavti.common.TextHelper;
import com.pavti.dal.AuthDal;
import com.pavti.dal.BusinessDal;
import com.pavti.dal.PlanDal;
import com.pavti.dataobject.common.ChartPoint;
import com.pavti.dataobject.platform.Business;
import com.pavti.dataobject.platform.BusinessListItem;
import com.pavti.dataobject.platform.BusinessSaveRequest;
import com.pavti.dataobject.platform.BusinessStatusRequest;
import com.pavti.dataobject.platform.Plan;
import com.pavti.dataobject.platform.PlatformStats;
import com.pavti.dataobject.platform.PlatformStatsRow;
import com.pavti.dataobject.platform.SettingsSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.PageAction;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Businesses for the platform admin, and the owner's own Settings page. */
@Service
public class BusinessBal {

    private static final Set<String> TYPES = Set.of("RESTAURANT", "CAFE", "SHOP", "SERVICE");
    private static final Set<String> LANGUAGES = Set.of("mr", "hi", "en");

    private final BusinessDal businessDal;
    private final PlanDal planDal;
    private final AuthDal authDal;
    private final RightsBal rights;
    private final AuditBal audit;
    private final PasswordHelper passwords;

    public BusinessBal(BusinessDal businessDal, PlanDal planDal, AuthDal authDal, RightsBal rights, AuditBal audit,
                       PasswordHelper passwords) {
        this.businessDal = businessDal;
        this.planDal = planDal;
        this.authDal = authDal;
        this.rights = rights;
        this.audit = audit;
        this.passwords = passwords;
    }

    public List<BusinessListItem> getAll() {
        return businessDal.getAll();
    }

    public Business get(long businessId) {
        return businessDal.getById(businessId).orElseThrow(() -> ApiException.notFound("Business not found."));
    }

    /** Add a business with its owner login, or edit one. Returns the business id. */
    @Transactional
    public long save(CurrentUser user, BusinessSaveRequest req) {
        boolean isNew = req.businessId() == null || req.businessId() == 0;
        rights.requirePage(user, "BUSINESSES", isNew ? PageAction.ADD : PageAction.EDIT);

        String name = clean(req.businessName());
        if (name.isEmpty()) throw ApiException.badRequest("Business name is required.");
        if (name.length() > 150) throw ApiException.badRequest("Business name is too long.");
        Plan plan = req.planId() == null ? null : planDal.getById(req.planId()).orElse(null);
        if (plan == null) throw ApiException.badRequest("Choose a plan.");
        String type = TYPES.contains(req.businessType()) ? req.businessType() : "RESTAURANT";
        String email = clean(req.email()).toLowerCase();
        if (!email.isEmpty() && !TextHelper.isEmail(email)) throw ApiException.badRequest("Email is not valid.");
        String mobile = TextHelper.mobile(req.mobileNo());
        if (!mobile.isEmpty() && !TextHelper.isMobile(mobile)) throw ApiException.badRequest("Mobile number is not valid.");
        String prefix = billPrefix(req.billPrefix(), name);
        String gstin = clean(req.gstin()).toUpperCase();
        String address = clean(req.addressLine());

        if (!isNew) {
            get(req.businessId());
            businessDal.update(req.businessId(), name, type, mobile, email, address, gstin, plan.planId(), prefix, user.userId());
            audit.log(req.businessId(), user.userId(), "BUSINESS_UPDATE", "businesses", req.businessId(), name, null);
            return req.businessId();
        }

        String ownerName = clean(req.ownerName());
        String ownerMobile = TextHelper.mobile(req.ownerMobileNo());
        if (ownerName.isEmpty()) throw ApiException.badRequest("Owner's name is required.");
        if (!TextHelper.isMobile(ownerMobile)) throw ApiException.badRequest("Owner's mobile number is not valid.");
        if (authDal.mobileExists(ownerMobile, null)) throw ApiException.conflict("This mobile number is already used.");
        if (clean(req.ownerPassword()).length() < 6) throw ApiException.badRequest("Password must be at least 6 characters.");

        AuthDal.RegisterResult r = authDal.registerBusiness(name, type, ownerName, ownerMobile, null,
                passwords.hash(clean(req.ownerPassword())), prefix, plan.planCode(), false, user.userId());
        businessDal.update(r.businessId(), name, type, mobile.isEmpty() ? ownerMobile : mobile, email, address, gstin,
                plan.planId(), prefix, user.userId());
        audit.log(r.businessId(), user.userId(), "BUSINESS_CREATE", "businesses", r.businessId(), name, null);
        return r.businessId();
    }

    public void setStatus(CurrentUser user, BusinessStatusRequest req) {
        Business b = get(req.businessId());
        String reason = clean(req.reason());
        if ("SUSPENDED".equals(req.status()) && reason.isEmpty()) throw ApiException.badRequest("Write a reason to pause a business.");
        businessDal.setStatus(b.businessId(), req.status(), reason, user.userId());
        audit.log(b.businessId(), user.userId(), "ACTIVE".equals(req.status()) ? "BUSINESS_ACTIVATE" : "BUSINESS_SUSPEND",
                "businesses", b.businessId(), b.businessName(), reason);
    }

    public PlatformStats stats() {
        PlatformStatsRow s = businessDal.stats();
        List<BusinessListItem> all = businessDal.getAll();
        List<ChartPoint> byBusiness = all.stream()
                .map(b -> new ChartPoint(b.businessName(), b.collected()))
                .sorted(Comparator.comparing(ChartPoint::value).reversed())
                .toList();
        List<ChartPoint> byPlan = planDal.getAll().stream()
                .map(p -> new ChartPoint(p.planName(), BigDecimal.valueOf(all.stream().filter(b -> b.planId() == p.planId()).count())))
                .toList();
        return new PlatformStats(s.businesses(), s.activeBusinesses(), s.businesses() - s.activeBusinesses(), s.users(), s.bills(),
                s.monthlyRevenue(), s.collected(), byBusiness, byPlan);
    }

    public Business saveSettings(CurrentUser user, SettingsSaveRequest req) {
        long bid = user.requireBusinessId();
        Business b = get(bid);
        String name = clean(req.businessName());
        if (name.isEmpty()) throw ApiException.badRequest("Business name is required.");
        BigDecimal tax = req.defaultTaxRate() == null ? BigDecimal.ZERO : req.defaultTaxRate();
        BigDecimal sc = req.serviceChargePct() == null ? BigDecimal.ZERO : req.serviceChargePct();
        if (tax.signum() < 0 || tax.compareTo(BigDecimal.valueOf(28)) > 0) throw ApiException.badRequest("Tax must be between 0 and 28%.");
        if (sc.signum() < 0 || sc.compareTo(BigDecimal.valueOf(20)) > 0) throw ApiException.badRequest("Service charge must be between 0 and 20%.");
        String email = clean(req.email()).toLowerCase();
        if (!email.isEmpty() && !TextHelper.isEmail(email)) throw ApiException.badRequest("Email is not valid.");
        String label = clean(req.serviceChargeLabel());
        SettingsSaveRequest cleaned = new SettingsSaveRequest(name, TextHelper.mobile(req.mobileNo()), email, clean(req.addressLine()),
                clean(req.gstin()).toUpperCase(), clean(req.billPrefix()).isEmpty() ? b.billPrefix() : billPrefix(req.billPrefix(), name),
                tax, sc, label.isEmpty() ? "Service charge" : label, clean(req.billFooterNote()),
                LANGUAGES.contains(req.defaultLanguage()) ? req.defaultLanguage() : b.defaultLanguage());
        businessDal.saveSettings(bid, cleaned, user.userId());
        audit.log(user, "SETTINGS_UPDATE", "businesses", bid, name, null);
        return get(bid);
    }

    /** "SPI-" from "Spice Garden" when empty; at most 10 characters. */
    private static String billPrefix(String given, String businessName) {
        String p = clean(given).toUpperCase();
        if (p.isEmpty()) {
            String letters = businessName.replaceAll("[^A-Za-z]", "").toUpperCase();
            p = (letters.length() >= 3 ? letters.substring(0, 3) : "BIL") + "-";
        }
        if (p.length() > 10) throw ApiException.badRequest("Bill prefix can be at most 10 characters.");
        return p;
    }
}
