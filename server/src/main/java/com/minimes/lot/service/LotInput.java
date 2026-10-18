package com.minimes.lot.service;

import java.math.BigDecimal;

/** 실적에 투입한 LOT 한 줄: 어느 LOT에서 몇 개를 썼는가 */
public record LotInput(String lotNo, BigDecimal qty) {
}
