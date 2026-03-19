package com.flowermarketplace.user.service;

import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.user.dto.AddressDto;
import com.flowermarketplace.user.dto.AddressRequest;
import com.flowermarketplace.user.dto.UpdateProfileRequest;
import com.flowermarketplace.user.dto.UserDto;
import com.flowermarketplace.user.entity.Address;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.mapper.UserMapper;
import com.flowermarketplace.user.repository.AddressRepository;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository    userRepository;
    private final AddressRepository addressRepository;
    private final UserMapper        userMapper;

    // ── UserDetailsService (Spring Security) ─────────────────────────────────

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.toDto(user);
    }

    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toDto(user);
    }

    public PagedResponse<UserDto> getAllUsers(int page, int size) {
        Page<User> users = userRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PagedResponse.of(users.map(userMapper::toDto));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public UserDto updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (request.getFirstName()      != null) user.setFirstName(request.getFirstName());
        if (request.getLastName()       != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber()    != null) {
            if (!request.getPhoneNumber().equals(user.getPhoneNumber())
                    && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                throw new BadRequestException("Phone number already in use.");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getCity()           != null) user.setCity(request.getCity());
        if (request.getBio()            != null) user.setBio(request.getBio());
        if (request.getProfileImageUrl() != null) user.setProfileImageUrl(request.getProfileImageUrl());

        user = userRepository.save(user);
        log.info("Profile updated for user {}", user.getId());
        return userMapper.toDto(user);
    }

    // ── Addresses ─────────────────────────────────────────────────────────────

    @Transactional
    public AddressDto addAddress(String email, AddressRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // If marking as default, unset any existing default
        if (request.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(user.getId())
                    .ifPresent(existing -> {
                        existing.setDefault(false);
                        addressRepository.save(existing);
                    });
        }

        Address address = Address.builder()
                .user(user)
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .addressLine(request.getAddressLine())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "VN")
                .isDefault(request.isDefault())
                .build();

        address = addressRepository.save(address);
        log.info("Address {} added for user {}", address.getId(), user.getId());
        return userMapper.toAddressDto(address);
    }

    @Transactional
    public void deleteAddress(String email, Long addressId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Address does not belong to current user.");
        }

        addressRepository.delete(address);
        log.info("Address {} deleted for user {}", addressId, user.getId());
    }

    public List<AddressDto> getAddresses(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return addressRepository.findByUserId(user.getId())
                .stream().map(userMapper::toAddressDto).toList();
    }

    // ── Internal helpers used by other services ───────────────────────────────

    public User getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public User getEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
