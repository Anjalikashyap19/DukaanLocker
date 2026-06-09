package com.shoplocker.fssai.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "shops")
@Data




public class Shop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String shopName;
	private String ownerName;

	private String mobile;

	public String getShopName() {
		return shopName;
	}

	public void setShopName(String shopName) {
		this.shopName = shopName;
	}

	public String getOwnerName(){
		return ownerName;
	}
	public void setOwnerName(String ownerName){
		this.ownerName=ownerName;

	}

	public String getMobile() {
		return mobile;
	}
	public void setMobile(String mobile){
		this.mobile =mobile;
	}
}



