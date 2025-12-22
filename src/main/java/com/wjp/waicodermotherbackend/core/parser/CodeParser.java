package com.wjp.waicodermotherbackend.core.parser;

/**
 * 代码解析器策略接口
 * 使用泛型：是因为我们并不能准确的知道到底是单文件还是多文件解析
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     * @param codeContent 代码内容
     * @return 解析结果
     */
    T parseCode(String codeContent);

}


