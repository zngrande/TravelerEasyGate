package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AgencyExportTemplate;

import java.util.List;

public interface AgencyExportTemplateDAO {
    void save(AgencyExportTemplate template);
    AgencyExportTemplate findById(int AETID);
    List<AgencyExportTemplate> findByAgency(int AID);
    AgencyExportTemplate findDefaultByAgency(int AID);
    void clearDefault(int AID);
    void delete(int AETID);
}
