package cn.qihuang02.graaljs.bridge;

import java.util.Collection;

/**
 * 允许对象在不修改自身结构的情况下向脚本注入额外成员。
 */
public interface CustomMemberProvider {
    Collection<CustomMember> getCustomMembers();
}
