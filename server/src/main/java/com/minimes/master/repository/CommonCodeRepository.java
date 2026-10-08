package com.minimes.master.repository;

import com.minimes.master.domain.CommonCode;
import com.minimes.master.domain.CommonCodeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, CommonCodeId> {

    List<CommonCode> findByGroupCodeOrderBySortOrder(String groupCode);
}
