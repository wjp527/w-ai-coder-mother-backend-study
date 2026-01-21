package com.wjp.waicodermotherbackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用代码生成相关枚举类
 * 
 * 该枚举类定义了应用代码生成过程中使用的各种配置值和常量。
 * 包括应用优先级、代码输出目录、部署目录等关键配置信息。
 * 
 * 主要用途：
 * 1. 定义应用优先级的标准值
 * 2. 配置代码生成和部署的目录路径
 * 3. 提供系统配置的统一管理
 * 4. 确保配置值的一致性和可维护性
 * 
 * 设计原则：
 * - 枚举值不可变，确保系统稳定性
 * - 提供中文描述，便于理解和维护
 * - 支持动态路径配置，适应不同环境
 * 
 * @author dhbxs
 * @since 2025/8/16
 * @version 1.0
 */
@Getter
public enum AppCodeGenEnum {

    /**
     * 精选应用优先级
     * 
     * 该枚举值定义了精选应用在系统中的优先级数值。
     * 精选应用通常具有更高的显示优先级，会在应用列表中优先展示。
     * 
     * 使用场景：
     * - 管理员标记的优质应用
     * - 系统推荐的热门应用
     * - 需要突出显示的特殊应用
     * 
     * 优先级规则：
     * - 数值越大，优先级越高
     * - 99是系统预留的最高优先级
     * - 普通应用的优先级通常为0
     * 
     * @example 在应用列表中，优先级为99的应用会排在最前面
     */
    GOOD_APP_PRIORITY("精选应用的优先级", "99"),
    
    /**
     * 默认应用优先级
     * 
     * 该枚举值定义了普通应用的默认优先级数值。
     * 新创建的应用如果没有特殊设置，将使用这个默认优先级。
     * 
     * 使用场景：
     * - 用户新创建的应用
     * - 未设置特殊优先级的应用
     * - 系统默认的应用排序
     * 
     * 优先级规则：
     * - 0是系统的基础优先级
     * - 低于精选应用的优先级
     * - 支持后续手动调整
     * 
     * @example 新创建的应用默认优先级为0，在列表中排在精选应用之后
     */
    DEFAULT_APP_PRIORITY("默认应用优先级", "0"),
    
    /**
     * 应用代码生成输出根目录
     * 
     * 该枚举值定义了AI生成的应用代码文件的存储根目录。
     * 所有通过AI代码生成器创建的应用代码都会保存到这个目录下。
     * 
     * 目录结构：
     * - 根目录：/tmp/code_output
     * - 子目录：每个应用有独立的子目录
     * - 文件类型：HTML、CSS、JavaScript等
     * 
     * 路径特点：
     * - 使用相对路径，基于项目根目录
     * - 支持跨平台兼容（Windows/Linux/Mac）
     * - 临时目录，可定期清理
     * 
     * @example 应用"blog_123"的代码将保存在 /tmp/code_output/blog_123/ 目录下
     */
    CODE_OUTPUT_ROOT_DIR("应用生成目录", System.getProperty("user.dir") + "/tmp/code_output"),
    
    /**
     * 应用部署根目录
     * 
     * 该枚举值定义了已部署应用的静态文件存储根目录。
     * 当应用被部署后，其文件会被复制到这个目录，用于生产环境访问。
     * 
     * 目录结构：
     * - 根目录：/tmp/code_deploy
     * - 子目录：每个部署的应用有独立的子目录
     * - 文件类型：部署后的静态资源文件
     * 
     * 部署流程：
     * - 从代码生成目录复制文件
     * - 生成唯一的部署标识
     * - 配置访问权限和域名
     * 
     * @example 部署后的应用"deploy_abc"将保存在 /tmp/code_deploy/deploy_abc/ 目录下
     */
    CODE_DEPLOY_ROOT_DIR("应用部署目录", System.getProperty("user.dir") + "/tmp/code_deploy");

    /**
     * 枚举值的中文描述
     * 
     * 该字段提供枚举值的中文说明，便于开发人员和用户理解每个枚举值的含义。
     * 中文描述在日志记录、错误提示、用户界面等场景中非常有用。
     * 
     * @example "精选应用的优先级"、"应用生成目录"
     */
    private final String text;
    
    /**
     * 枚举值的实际数值
     * 
     * 该字段存储枚举值对应的实际配置值，可以是字符串、数字或路径等。
     * 系统在运行时使用这个值进行具体的业务逻辑处理。
     * 
     * @example "99"、"/tmp/code_output"、"/tmp/code_deploy"
     */
    private final String value;

    /**
     * 私有构造函数
     * 
     * 枚举构造函数是私有的，确保枚举值只能在类内部定义。
     * 这种设计保证了枚举的不可变性，符合枚举的设计原则。
     * 
     * @param text 枚举值的中文描述
     * @param value 枚举值的实际数值
     */
    AppCodeGenEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}
