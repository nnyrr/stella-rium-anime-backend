package top.stellarium.service;

import top.stellarium.pojo.dto.CollectionDTO;
import top.stellarium.pojo.vo.CollectionAnimeVO;
import top.stellarium.pojo.vo.CollectionCharacterVO;
import top.stellarium.pojo.vo.ListVO;

public interface CollectionService {
    ListVO<CollectionAnimeVO> getCollectedAnime(CollectionDTO collectionDTO);

    ListVO<CollectionCharacterVO> getCollectedCharacter(CollectionDTO collectionDTO);
}
