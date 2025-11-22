package top.stellarium.service;

import top.stellarium.pojo.dto.LibraryDTO;
import top.stellarium.pojo.vo.LibraryAnimeVO;
import top.stellarium.pojo.vo.ListVO;

public interface LibraryService {

    /**
     * 获得排行榜
     * @param libraryDTO
     * @return
     */
    ListVO<LibraryAnimeVO> getLibrary(LibraryDTO libraryDTO);
}
