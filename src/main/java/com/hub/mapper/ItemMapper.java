package com.hub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hub.domain.po.Item;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
}
