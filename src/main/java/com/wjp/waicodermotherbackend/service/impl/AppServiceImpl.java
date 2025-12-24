package com.wjp.waicodermotherbackend.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjp.waicodermotherbackend.model.entity.App;
import com.wjp.waicodermotherbackend.mapper.AppMapper;
import com.wjp.waicodermotherbackend.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
