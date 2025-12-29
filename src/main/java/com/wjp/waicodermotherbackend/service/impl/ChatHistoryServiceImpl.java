package com.wjp.waicodermotherbackend.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjp.waicodermotherbackend.model.entity.ChatHistory;
import com.wjp.waicodermotherbackend.mapper.ChatHistoryMapper;
import com.wjp.waicodermotherbackend.service.ChatHistoryService;
import org.springframework.stereotype.Service;

/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

}
