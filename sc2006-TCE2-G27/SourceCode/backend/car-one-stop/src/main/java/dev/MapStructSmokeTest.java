package com.sc2006group5.car_one_stop.dev;

public class MapStructSmokeTest {
    public static void main(String[] args) {
        UserEntity e = new UserEntity();
        e.setId(1L);
        e.setEmail("a@b.com");

        UserDto dto = UserMapper.INSTANCE.toDto(e);
        System.out.println(dto.getEmail());
    }
}