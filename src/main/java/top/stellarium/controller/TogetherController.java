package top.stellarium.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.stellarium.common.result.Result;
import top.stellarium.pojo.dto.CreateRoomDTO;
import top.stellarium.pojo.entity.RoomInfo;
import top.stellarium.pojo.vo.CreateRoomVO;
import top.stellarium.service.TogetherService;

import java.util.List;

@RestController
@RequestMapping("/together")
@Slf4j
@Tag(name = "一起看相关接口")
public class TogetherController {

    @Autowired
    private TogetherService togetherService;

    @PostMapping("")
    public Result<CreateRoomVO> createRoom(CreateRoomDTO createRoomDTO){
        log.info("创建房间: {}", createRoomDTO);
        CreateRoomVO createRoomVO = togetherService.createRoom(createRoomDTO);
        return Result.success(createRoomVO);
    }

    @GetMapping("")
    public Result<List<RoomInfo>> getRooms(){
        log.info("获取房间列表");
        List<RoomInfo> roomInfos = togetherService.getRooms();
        return Result.success(roomInfos);
    }
}
