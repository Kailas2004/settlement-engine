package com.kailas.settlementengine.repository;

import com.kailas.settlementengine.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
}
