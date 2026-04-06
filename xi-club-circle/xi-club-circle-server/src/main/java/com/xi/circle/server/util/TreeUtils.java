package com.jingdianjichi.circle.server.util;

import com.xi.circle.api.common.TreeNode;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 该工具类是通用的树形数据处理工具，核心解决两个问题：
 * 扁平数据 → 树形结构的转换；
 * 树形结构中按目标 ID 递归筛选节点（含子节点）；
 * 整体采用流式编程 + 递归实现，简洁且通用，适用于所有树形结构场景。
 */
public class TreeUtils {

    /**
     * 1. 构建树形结构（buildTree方法）
     * 作用：将扁平的节点列表转换为层级化的树形结构（根节点列表）。核心逻辑：
     * 入参是扁平的TreeNode子类列表（如部门、菜单、分类等树形数据）；
     * 先判空，空则返回空列表；
     * 通过groupingBy按nodePId（父节点 ID）分组，构建 “父节点 ID → 子节点列表” 的映射关系；
     * 遍历所有节点，为每个节点从分组映射中匹配其子节点列表并设置（setChildren）；
     * 最终过滤出所有 “根节点”（getRootNode()返回true的节点），返回根节点列表（每个根节点已包含完整的子节点层级）。
     * @param nodes
     * @return
     * @param <T>
     */
    public static <T extends TreeNode> List<T> buildTree(List<T> nodes) {

        if (CollectionUtils.isEmpty(nodes)) {
            return Collections.emptyList();
        }
        Map<Long, List<TreeNode>> groups = nodes.stream().collect(Collectors.groupingBy(TreeNode::getNodePId));
        return nodes.stream().filter(Objects::nonNull).peek(pnd -> {
            List<TreeNode> ts = groups.get(pnd.getNodeId());
            pnd.setChildren(ts);
        }).filter(TreeNode::getRootNode).collect(Collectors.toList());

    }

    /**
     * (1) findAll（对外暴露的递归查询方法）
     * 作用：从树形节点中，递归查找所有与targetId（目标 ID）相关的节点（自身或父节点匹配targetId的节点）。
     * 核心逻辑：
     * 若当前节点的nodeId（自身 ID）或nodePId（父 ID）等于targetId，则将该节点及其所有子节点加入结果集；
     * 若不匹配，则递归遍历当前节点的子节点，继续查找；
     * @param result
     * @param node
     * @param targetId
     * @param <T>
     */
    public static <T extends TreeNode> void findAll(List<T> result, TreeNode node, Long targetId) {

        if (node.getNodeId().equals(targetId) || node.getNodePId().equals(targetId)) {
            addAll(result, node);
        } else {
            if (!CollectionUtils.isEmpty(node.getChildren())) {
                for (TreeNode child : node.getChildren()) {
                    findAll(result, child, targetId);
                }
            }
        }

    }

    /**
     * addAll（私有辅助方法，递归收集节点）
     * 作用：递归收集某个节点及其所有子节点，加入结果列表。
     * 核心逻辑：
     * 先将当前节点加入结果集；
     * 若当前节点有子节点，递归遍历子节点并重复上述操作（深度优先）。
     * @param result
     * @param node
     * @param <T>
     */
    private static <T extends TreeNode> void addAll(List<T> result, TreeNode node) {
        result.add((T) node);
        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (TreeNode child : node.getChildren()) {
                addAll(result, child);
            }
        }
    }

}
