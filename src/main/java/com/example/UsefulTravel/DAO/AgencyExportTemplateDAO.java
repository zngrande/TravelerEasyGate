package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AgencyExportTemplate;

import java.util.List;

public interface AgencyExportTemplateDAO {
    void save(AgencyExportTemplate template);
    AgencyExportTemplate findById(int AETID);
    List<AgencyExportTemplate> findByAgency(int AID);
    // type: "CUSTOMER" (給客戶的 Word 範本) 或 "AGENCY" (給同業的 Excel 範本)
    List<AgencyExportTemplate> findByAgencyAndType(int AID, String type);
    AgencyExportTemplate findDefaultByAgency(int AID);
    AgencyExportTemplate findDefaultByAgencyAndType(int AID, String type);
    void clearDefault(int AID);
    void clearDefault(int AID, String type);
    void delete(int AETID);
}
