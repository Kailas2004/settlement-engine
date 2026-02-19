package com.kailas.settlementengine.repository;

import com.kailas.settlementengine.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
