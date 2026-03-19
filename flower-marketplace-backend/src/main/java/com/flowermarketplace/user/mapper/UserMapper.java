package com.flowermarketplace.user.mapper;

import com.flowermarketplace.user.dto.AddressDto;
import com.flowermarketplace.user.dto.UserDto;
import com.flowermarketplace.user.entity.Address;
import com.flowermarketplace.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName",
             expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    UserDto toDto(User user);

    AddressDto toAddressDto(Address address);
}
