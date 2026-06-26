package com.shoplocker.fssai.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.persistence.Column;
@Entity
@Table(name = "shops")
@Data



public class Shop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message= "shop name is required")
	private String shopName;

	@NotBlank(message = "owner name is required")
	private String ownerName;

    @NotBlank(message ="mobile number is required")
	@Pattern(
			regexp = "^[0-9]{10}$",
			message = "Mobile number must be 10 digits"
	)
	@Column(unique=true)
	private String mobile;

	public Long getId(){
		return id;
	}

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




