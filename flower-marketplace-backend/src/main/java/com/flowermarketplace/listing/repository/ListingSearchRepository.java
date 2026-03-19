package com.flowermarketplace.listing.repository;

import com.flowermarketplace.listing.entity.ListingDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingSearchRepository extends ElasticsearchRepository<ListingDocument, String> {
}
