package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ImageAsset;

import java.util.List;

public interface ImageAssetDAO {
    void save(ImageAsset image);
    ImageAsset findById(int IAID);
    List<ImageAsset> findByAgency(int AID);
    List<ImageAsset> findByPoi(int PID);
    List<ImageAsset> findUnlinked(int AID);
    void deleteById(int IAID);
    void clearMatchedPid(int PID);
}
