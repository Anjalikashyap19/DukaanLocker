package com.shoplocker.fssai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;


import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.service.ShopService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/shops")
public class ShopController {

    @Autowired
    private ShopService shopService;

    @PostMapping
    public Shop createShop( @Valid @RequestBody Shop shop) {
        return shopService.saveShop(shop);
    }

    @GetMapping
    public List<Shop> getAllShops() {
        return shopService.getAllShops();
    }
    
    @GetMapping("{id}")
    public Shop getShop(@PathVariable Long id) {
        return shopService.getShopById(id);
    }

    @PutMapping("{id}")
    public Shop updateShop(@PathVariable Long id, @RequestBody Shop shop ){ return shopService.updateDetails(id,shop);}


    @DeleteMapping("{id}")
    public String deleteShop(@PathVariable Long id){
        shopService.deleteShop(id);

        return "shop deleted successfully";

    }

}

