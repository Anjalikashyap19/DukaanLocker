package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/docs")
public class DocumentController {

    private final PANDocumentService panDocumentService;
    private final ShopEstablishmentDocumentService shopEstablishmentDocumentService;
    private final TradeLicenseDocumentService tradeLicenseDocumentService;
    private final MSMEDocumentService msmeDocumentService;
    private final ProfessionalTaxDocumentService professionalTaxDocumentService;
    private final TrademarkDocumentService trademarkDocumentService;
    private final PropertyTaxDocumentService propertyTaxDocumentService;
    private final IECDocumentService iecDocumentService;
    private final PollutionControlDocumentService pollutionControlDocumentService;
    private final FireSafetyDocumentService fireSafetyDocumentService;
    private final LabourLicenseDocumentService labourLicenseDocumentService;
    private final ShopInsuranceDocumentService shopInsuranceDocumentService;
    private final DrugLicenseDocumentService drugLicenseDocumentService;

    public DocumentController(PANDocumentService panDocumentService,
                              ShopEstablishmentDocumentService shopEstablishmentDocumentService,
                              TradeLicenseDocumentService tradeLicenseDocumentService,
                              MSMEDocumentService msmeDocumentService,
                              ProfessionalTaxDocumentService professionalTaxDocumentService,
                              TrademarkDocumentService trademarkDocumentService,
                              PropertyTaxDocumentService propertyTaxDocumentService,
                              IECDocumentService iecDocumentService,
                              PollutionControlDocumentService pollutionControlDocumentService,
                              FireSafetyDocumentService fireSafetyDocumentService,
                              LabourLicenseDocumentService labourLicenseDocumentService,
                              ShopInsuranceDocumentService shopInsuranceDocumentService,
                              DrugLicenseDocumentService drugLicenseDocumentService) {
        this.panDocumentService = panDocumentService;
        this.shopEstablishmentDocumentService = shopEstablishmentDocumentService;
        this.tradeLicenseDocumentService = tradeLicenseDocumentService;
        this.msmeDocumentService = msmeDocumentService;
        this.professionalTaxDocumentService = professionalTaxDocumentService;
        this.trademarkDocumentService = trademarkDocumentService;
        this.propertyTaxDocumentService = propertyTaxDocumentService;
        this.iecDocumentService = iecDocumentService;
        this.pollutionControlDocumentService = pollutionControlDocumentService;
        this.fireSafetyDocumentService = fireSafetyDocumentService;
        this.labourLicenseDocumentService = labourLicenseDocumentService;
        this.shopInsuranceDocumentService = shopInsuranceDocumentService;
        this.drugLicenseDocumentService = drugLicenseDocumentService;
    }

    // ─── PAN / TAN ──────────────────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/pan/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadPAN(@PathVariable Long shopId,
                                            @RequestParam("file") MultipartFile file) {
        panDocumentService.uploadPAN(shopId, file);
        return ResponseEntity.ok("PAN/TAN document uploaded successfully");
    }

    // ─── Shop & Establishment License ──────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/shop-establishment/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadShopEstablishment(@PathVariable Long shopId,
                                                          @RequestParam("file") MultipartFile file) {
        shopEstablishmentDocumentService.uploadShopEstablishment(shopId, file);
        return ResponseEntity.ok("Shop & Establishment License uploaded successfully");
    }

    // ─── Trade License ──────────────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/trade-license/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadTradeLicense(@PathVariable Long shopId,
                                                     @RequestParam("file") MultipartFile file) {
        tradeLicenseDocumentService.uploadTradeLicense(shopId, file);
        return ResponseEntity.ok("Trade License uploaded successfully");
    }

    // ─── Udyam MSME Registration ────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/msme/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadMSME(@PathVariable Long shopId,
                                             @RequestParam("file") MultipartFile file) {
        msmeDocumentService.uploadMSME(shopId, file);
        return ResponseEntity.ok("Udyam MSME Registration uploaded successfully");
    }

    // ─── Professional Tax Registration ──────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/professional-tax/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadProfessionalTax(@PathVariable Long shopId,
                                                        @RequestParam("file") MultipartFile file) {
        professionalTaxDocumentService.uploadProfessionalTax(shopId, file);
        return ResponseEntity.ok("Professional Tax Registration uploaded successfully");
    }

    // ─── Trademark ──────────────────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/trademark/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadTrademark(@PathVariable Long shopId,
                                                  @RequestParam("file") MultipartFile file) {
        trademarkDocumentService.uploadTrademark(shopId, file);
        return ResponseEntity.ok("Trademark uploaded successfully");
    }

    // ─── Property Tax Certificate ───────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/property-tax/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadPropertyTax(@PathVariable Long shopId,
                                                    @RequestParam("file") MultipartFile file) {
        propertyTaxDocumentService.uploadPropertyTax(shopId, file);
        return ResponseEntity.ok("Property Tax certificate uploaded successfully");
    }

    // ─── Import Export Code ──────────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/iec/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadIEC(@PathVariable Long shopId,
                                            @RequestParam("file") MultipartFile file) {
        iecDocumentService.uploadIEC(shopId, file);
        return ResponseEntity.ok("Import Export Code uploaded successfully");
    }

    // ─── Pollution Control Certificate ──────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/pollution-control/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadPollutionControl(@PathVariable Long shopId,
                                                         @RequestParam("file") MultipartFile file) {
        pollutionControlDocumentService.uploadPollutionControl(shopId, file);
        return ResponseEntity.ok("Pollution Control Certificate uploaded successfully");
    }

    // ─── Fire Safety Certificate ────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/fire-safety/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadFireSafety(@PathVariable Long shopId,
                                                   @RequestParam("file") MultipartFile file) {
        fireSafetyDocumentService.uploadFireSafety(shopId, file);
        return ResponseEntity.ok("Fire Safety Certificate uploaded successfully");
    }

    // ─── Labour License / Workmen Compensation Policy ──────────────────────────
    @PostMapping(value = "/shops/{shopId}/labour-license/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadLabourLicense(@PathVariable Long shopId,
                                                      @RequestParam("file") MultipartFile file) {
        labourLicenseDocumentService.uploadLabourLicense(shopId, file);
        return ResponseEntity.ok("Labour License / Workmen Compensation Policy uploaded successfully");
    }

    // ─── Shop Insurance ──────────────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/shop-insurance/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadShopInsurance(@PathVariable Long shopId,
                                                      @RequestParam("file") MultipartFile file) {
        shopInsuranceDocumentService.uploadShopInsurance(shopId, file);
        return ResponseEntity.ok("Shop Insurance uploaded successfully");
    }

    // ─── Drug License ────────────────────────────────────────────────────────────
    @PostMapping(value = "/shops/{shopId}/drug-license/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadDrugLicense(@PathVariable Long shopId,
                                                    @RequestParam("file") MultipartFile file) {
        drugLicenseDocumentService.uploadDrugLicense(shopId, file);
        return ResponseEntity.ok("Drug License uploaded successfully");
    }
}
