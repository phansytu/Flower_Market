package com.flowermarketplace.listing.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(indexName = "listings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String condition;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String location;

    @Field(type = FieldType.Text)
    private String tags;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @Field(type = FieldType.Text)
    private String sellerName;

    @Field(type = FieldType.Double)
    private double averageRating;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;
}
