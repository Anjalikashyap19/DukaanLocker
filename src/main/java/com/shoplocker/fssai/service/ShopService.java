package com.shoplocker.fssai.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.ShopRepository;

@Service
public class ShopService {

	@Autowired
	private ShopRepository shopRepository;
	// used for store incpoming shop details

	public Shop saveShop(Shop shop) {
		;
		return shopRepository.save(shop);
	}

	public List<Shop> getAllShops() {

		return shopRepository.findAll();
	}

	// get shop by id
	// input: id
	// output: shop details

	public Shop etytrt(long id) {

		return shopRepository.findById(id).get();

	}

	// update method by id
	//input id: id
	//new detail

	public Shop updateDetails(long id,Shop shop){
		Shop existingShop = shopRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("shop not found"));

		existingShop.setShopName(shop.getShopName());
		existingShop.setOwnerName(shop.getOwnerName());
		existingShop.setMobile(shop.getMobile());



		return  shopRepository.save(existingShop);




	}

}