package com.pavti.dal;

import com.pavti.dataobject.customer.CustomerListItem;
import com.pavti.dataobject.customer.CustomerRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerDal {

    private final DataClass db;

    public CustomerDal(DataClass db) {
        this.db = db;
    }

    public List<CustomerListItem> getAll(long businessId) {
        return db.getList("sp_customer_get_all", CustomerListItem.class, businessId);
    }

    public Optional<CustomerRow> getById(long customerId, long businessId) {
        return db.getOne("sp_customer_get_by_id", CustomerRow.class, customerId, businessId);
    }

    public long save(Long customerId, long businessId, String name, String mobileNo, String email, String addressLine, String gstin,
                     long by) {
        return db.getLong("sp_customer_save", customerId, businessId, name, mobileNo, email, addressLine, gstin, by);
    }

    public void delete(long customerId, long businessId, long by) {
        db.execute("sp_customer_delete", customerId, businessId, by);
    }
}
