package com.wjp.waicodermotherbackend.controller;

import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.wjp.waicodermotherbackend.model.entity.ChatHistoryOriginal;
import com.wjp.waicodermotherbackend.service.ChatHistoryOriginalService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 对话历史（加载对话记忆，包括工具调用信息） 控制层。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@RestController
@RequestMapping("/chatHistoryOriginal")
public class ChatHistoryOriginalController {

    @Autowired
    private ChatHistoryOriginalService chatHistoryOriginalService;

    /**
     * 保存对话历史（加载对话记忆，包括工具调用信息）。
     *
     * @param chatHistoryOriginal 对话历史（加载对话记忆，包括工具调用信息）
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody ChatHistoryOriginal chatHistoryOriginal) {
        return chatHistoryOriginalService.save(chatHistoryOriginal);
    }

    /**
     * 根据主键删除对话历史（加载对话记忆，包括工具调用信息）。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable Long id) {
        return chatHistoryOriginalService.removeById(id);
    }

    /**
     * 根据主键更新对话历史（加载对话记忆，包括工具调用信息）。
     *
     * @param chatHistoryOriginal 对话历史（加载对话记忆，包括工具调用信息）
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public boolean update(@RequestBody ChatHistoryOriginal chatHistoryOriginal) {
        return chatHistoryOriginalService.updateById(chatHistoryOriginal);
    }

    /**
     * 查询所有对话历史（加载对话记忆，包括工具调用信息）。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<ChatHistoryOriginal> list() {
        return chatHistoryOriginalService.list();
    }

    /**
     * 根据主键获取对话历史（加载对话记忆，包括工具调用信息）。
     *
     * @param id 对话历史（加载对话记忆，包括工具调用信息）主键
     * @return 对话历史（加载对话记忆，包括工具调用信息）详情
     */
    @GetMapping("getInfo/{id}")
    public ChatHistoryOriginal getInfo(@PathVariable Long id) {
        return chatHistoryOriginalService.getById(id);
    }

    /**
     * 分页查询对话历史（加载对话记忆，包括工具调用信息）。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<ChatHistoryOriginal> page(Page<ChatHistoryOriginal> page) {
        return chatHistoryOriginalService.page(page);
    }

}
