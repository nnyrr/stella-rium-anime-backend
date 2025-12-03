package top.stellarium.service;

import top.stellarium.pojo.dto.CreateRoomDTO;
import top.stellarium.pojo.entity.RoomInfo;
import top.stellarium.pojo.vo.CreateRoomVO;

import java.util.List;

public interface TogetherService {
    CreateRoomVO createRoom(CreateRoomDTO createRoomDTO);

    List<RoomInfo> getRooms();
}
