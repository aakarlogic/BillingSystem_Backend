package com.pavti.dal;

import com.pavti.dataobject.platform.Business;
import com.pavti.dataobject.platform.BusinessListItem;
import com.pavti.dataobject.platform.PlatformStatsRow;
import com.pavti.dataobject.platform.SettingsSaveRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Businesses (tenants) and platform numbers. */
@Repository
public class BusinessDal {

    private final DataClass db;

    public BusinessDal(DataClass db) {
        this.db = db;
    }

    public List<BusinessListItem> getAll() {
        return db.getList("sp_business_get_all", BusinessListItem.class);
    }

    public Optional<Business> getById(long businessId) {
        return db.getOne("sp_business_get_by_id", Business.class, businessId);
    }

    public void update(long businessId, String name, String type, String mobileNo, String email, String addressLine, String gstin,
                       long planId, String billPrefix, long by) {
        db.execute("sp_business_update", businessId, name, type, mobileNo, email, addressLine, gstin, planId, billPrefix, by);
    }

    public void setStatus(long businessId, String status, String reason, long by) {
        db.execute("sp_business_set_status", businessId, status, reason, by);
    }

    /** req values are already cleaned by BusinessBal. */
    public void saveSettings(long businessId, SettingsSaveRequest r, long by) {
        db.execute("sp_business_save_settings", businessId, r.businessName(), r.mobileNo(), r.email(), r.addressLine(), r.gstin(),
                r.billPrefix(), r.defaultTaxRate(), r.serviceChargePct(), r.serviceChargeLabel(), r.billFooterNote(),
                r.defaultLanguage(), by);
    }

    public PlatformStatsRow stats() {
        return db.getOne("sp_platform_stats", PlatformStatsRow.class).orElseThrow();
    }
}
