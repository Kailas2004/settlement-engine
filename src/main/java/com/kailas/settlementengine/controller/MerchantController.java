package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.entity.Merchant;
import com.kailas.settlementengine.repository.MerchantRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/merchants")
public class MerchantController {
    private final MerchantRepository merchantRepository;

    public MerchantController(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @PostMapping
    public Merchant createMerchant(@RequestBody Merchant merchant) {
        return merchantRepository.save(merchant);
    }

    @GetMapping
    public List<Merchant> getAllMerchants() {
        return merchantRepository.findAll();
    }
}
