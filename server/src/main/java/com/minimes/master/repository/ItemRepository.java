package com.minimes.master.repository;

import com.minimes.master.domain.Item;
import com.minimes.master.domain.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, String> {

    // 메서드 이름으로 쿼리를 만든다: SELECT ... WHERE item_type = ? ORDER BY item_code
    List<Item> findByItemTypeOrderByItemCode(ItemType itemType);

    List<Item> findAllByOrderByItemCode();
}
