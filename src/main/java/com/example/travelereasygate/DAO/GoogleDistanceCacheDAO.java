package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.GoogleDistanceCache;

import java.math.BigDecimal;

public interface GoogleDistanceCacheDAO {
    // 查快取, 座標須先四捨五入到小數點後5位 (跟寫入時的 key 規則一致), 沒命中回傳 null
    GoogleDistanceCache find(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng, String mode);

    // 寫入快取; 失敗 (例如極少見的併發競態導致 unique key 衝突) 不會拋出例外, 只會記 log 略過
    void save(GoogleDistanceCache cache);
}
