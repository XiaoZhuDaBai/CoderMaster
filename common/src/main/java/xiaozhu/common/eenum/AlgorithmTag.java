package xiaozhu.common.eenum;

/**
 * 算法/数据结构标签枚举
 * 用于类型安全、验证和代码提示
 *
 * @author XiaoZhuDaBai
 */
public enum AlgorithmTag {
    // 数据结构类 (1-16)
    ARRAY(1, "数组", "数据结构"),
    STRING(2, "字符串", "数据结构"),
    LINKED_LIST(3, "链表", "数据结构"),
    STACK(4, "栈", "数据结构"),
    QUEUE(5, "队列", "数据结构"),
    TREE(6, "树", "数据结构"),
    GRAPH(7, "图", "数据结构"),
    HASH_TABLE(8, "哈希表", "数据结构"),
    HEAP(9, "堆", "数据结构"),
    UNION_FIND(10, "并查集", "数据结构"),
    TRIE(11, "字典树", "数据结构"),
    SEGMENT_TREE(12, "线段树", "数据结构"),
    FENWICK_TREE(13, "树状数组", "数据结构"),
    PREFIX_SUM(14, "前缀和", "数据结构"),
    DIFFERENCE_ARRAY(15, "差分数组", "数据结构"),
    AVL_TREE(16, "平衡二叉树", "数据结构"),

    // 算法类 (17-40)
    DYNAMIC_PROGRAMMING(17, "动态规划", "算法"),
    GREEDY(18, "贪心", "算法"),
    BACKTRACKING(19, "回溯", "算法"),
    DIVIDE_CONQUER(20, "分治", "算法"),
    SORTING(21, "排序", "算法"),
    SEARCH(22, "搜索", "算法"),
    DFS(23, "深度优先搜索", "算法"),
    BFS(24, "广度优先搜索", "算法"),
    TWO_POINTERS(25, "双指针", "算法"),
    SLIDING_WINDOW(26, "滑动窗口", "算法"),
    BIT_MANIPULATION(27, "位运算", "算法"),
    MATH(28, "数学", "算法"),
    SIMULATION(29, "模拟", "算法"),
    BINARY_SEARCH(30, "二分查找", "算法"),
    TOPOLOGICAL_SORT(31, "拓扑排序", "算法"),
    SHORTEST_PATH(32, "最短路径", "算法"),
    MIN_SPANNING_TREE(33, "最小生成树", "算法"),
    STRING_MATCHING(34, "字符串匹配", "算法"),
    NUMBER_THEORY(35, "数论", "算法"),
    COMBINATORICS(36, "组合数学", "算法"),
    GEOMETRY(37, "几何", "算法"),
    GAME_THEORY(38, "博弈论", "算法"),
    CONSTRUCTION(39, "构造", "算法"),
    INTERACTIVE(40, "交互", "算法");

    private final Integer tagId;
    private final String tagName;
    private final String category;

    AlgorithmTag(Integer tagId, String tagName, String category) {
        this.tagId = tagId;
        this.tagName = tagName;
        this.category = category;
    }

    public Integer getTagId() {
        return tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public String getCategory() {
        return category;
    }

    /**
     * 根据ID获取标签
     */
    public static AlgorithmTag fromId(Integer tagId) {
        for (AlgorithmTag tag : values()) {
            if (tag.tagId.equals(tagId)) {
                return tag;
            }
        }
        throw new IllegalArgumentException("Invalid tag ID: " + tagId);
    }

    /**
     * 验证标签ID是否有效
     */
    public static boolean isValid(Integer tagId) {
        try {
            fromId(tagId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}