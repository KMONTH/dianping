package com.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.dto.Result;
import com.dp.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result getSeckillVoucher(Long voucherId);
    void creatVoucherOrder(VoucherOrder voucherOrder);
}
