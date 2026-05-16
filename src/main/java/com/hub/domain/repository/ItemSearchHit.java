package com.hub.domain.repository;

import com.hub.domain.po.Item;

public record ItemSearchHit(Item item, double score, double distance) {

}
