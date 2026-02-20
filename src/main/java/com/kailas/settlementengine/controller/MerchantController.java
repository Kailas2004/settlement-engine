package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.entity.Merchant;
import com.kailas.settlementengine.repository.MerchantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> createMerchant(@RequestBody Merchant merchant) {
        try {
            Merchant saved = merchantRepository.save(merchant);
            return ResponseEntity.ok(saved);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.badRequest()
                    .body("Merchant with same name and bank account already exists.");
        }
    }

    @GetMapping
    public List<Merchant> getAllMerchants() {
        return merchantRepository.findAll();
    }
}