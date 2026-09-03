package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.AgencyExportTemplate;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgencyExportTemplateDAOImpl implements AgencyExportTemplateDAO {

    private final EntityManager em;

    @Autowired
    public AgencyExportTemplateDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(AgencyExportTemplate template) {
        if (template.getCreatedAt() == null) {
            template.setCreatedAt(LocalDateTime.now());
        }
        if (template.getAETID() == 0) {
            em.persist(template);
        } else {
            em.merge(template);
        }
    }

    @Override
    public AgencyExportTemplate findById(int AETID) {
        return em.find(AgencyExportTemplate.class, AETID);
    }

    @Override
    public List<AgencyExportTemplate> findByAgency(int AID) {
        return em.createQuery(
                "SELECT t FROM AgencyExportTemplate t WHERE t.AID = :aid ORDER BY t.createdAt DESC",
                AgencyExportTemplate.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    public List<AgencyExportTemplate> findByAgencyAndType(int AID, String type) {
        return em.createQuery(
                "SELECT t FROM AgencyExportTemplate t WHERE t.AID = :aid AND t.templateType = :type ORDER BY t.createdAt DESC",
                AgencyExportTemplate.class)
                .setParameter("aid", AID)
                .setParameter("type", type)
                .getResultList();
    }

    @Override
    public AgencyExportTemplate findDefaultByAgency(int AID) {
        return em.createQuery(
                "SELECT t FROM AgencyExportTemplate t WHERE t.AID = :aid AND t.isDefault = true",
                AgencyExportTemplate.class)
                .setParameter("aid", AID)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    public AgencyExportTemplate findDefaultByAgencyAndType(int AID, String type) {
        return em.createQuery(
                "SELECT t FROM AgencyExportTemplate t WHERE t.AID = :aid AND t.templateType = :type AND t.isDefault = true",
                AgencyExportTemplate.class)
                .setParameter("aid", AID)
                .setParameter("type", type)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    @Transactional
    public void clearDefault(int AID) {
        em.createQuery("UPDATE AgencyExportTemplate t SET t.isDefault = false WHERE t.AID = :aid")
                .setParameter("aid", AID)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void clearDefault(int AID, String type) {
        em.createQuery("UPDATE AgencyExportTemplate t SET t.isDefault = false WHERE t.AID = :aid AND t.templateType = :type")
                .setParameter("aid", AID)
                .setParameter("type", type)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void delete(int AETID) {
        AgencyExportTemplate t = findById(AETID);
        if (t != null) em.remove(t);
    }
}
