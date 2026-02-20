package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.entity.Customer;
import com.kailas.settlementengine.entity.Merchant;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.repository.CustomerRepository;
import com.kailas.settlementengine.repository.MerchantRepository;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final MerchantRepository merchantRepository;

    public TransactionController(TransactionRepository transactionRepository,
                                 CustomerRepository customerRepository,
                                 MerchantRepository merchantRepository) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.merchantRepository = merchantRepository;
    }

    @PostMapping
    public Transaction createTransaction(@RequestParam Long customerId,
                                         @RequestParam Long merchantId,
                                         @RequestParam BigDecimal amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        Transaction transaction = new Transaction();
        transaction.setCustomer(customer);
        transaction.setMerchant(merchant);
        transaction.setAmount(amount);

        return transactionRepository.save(transaction);
    }

    @GetMapping
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
}
