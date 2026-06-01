/**
 * API 工具类
 * 统一处理 API 请求、认证、错误处理
 */

// 开发环境使用代理（相对路径），生产环境使用完整 URL
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 
  (import.meta.env.DEV ? '' : 'http://localhost:8080')

/**
 * 自定义 JSON 解析器，处理大数字精度问题
 * 将超过安全范围的数字解析为字符串
 */
function bigIntSafeJSONParse(jsonString) {
  // 使用正则替换超过安全范围的数字为字符串
  return JSON.parse(jsonString, (key, value) => {
    if (typeof value === 'string' && /^\d{16,}$/.test(value)) {
      // 16位以上的数字字符串保持为字符串
      return value
    }
    if (typeof value === 'number' && !Number.isSafeInteger(value)) {
      // 超过安全范围的数字转为字符串
      return String(value)
    }
    return value
  })
}

/**
 * 统一响应格式
 * @typedef {Object} ResponseResult
 * @property {number} code - 状态码
 * @property {boolean} status - 是否成功
 * @property {string} message - 响应消息
 * @property {T} data - 响应数据
 */

/**
 * 获取 token
 */
function getToken() {
  return localStorage.getItem('token')
}

/**
 * 设置 token
 */
function setToken(token) {
  if (token) {
    localStorage.setItem('token', token)
  } else {
    localStorage.removeItem('token')
  }
}

/**
 * 统一请求方法
 * @param {string} url - 请求 URL
 * @param {RequestInit} options - 请求选项
 * @returns {Promise<ResponseResult<T>>}
 */
async function request(url, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  }

  // 添加认证 token
  const token = getToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  try {
    const response = await fetch(`${API_BASE_URL}${url}`, {
      ...options,
      headers,
    })

    const data = await response.json()

    // 如果响应不成功，抛出错误
    if (!response.ok || !data.status) {
      throw new Error(data.message || `请求失败: ${response.status}`)
    }

    return data
  } catch (error) {
    // 网络错误或其他错误
    throw new Error(error.message || '网络请求失败，请稍后重试')
  }
}

/**
 * API 方法
 */
export const api = {
  /**
   * 发送验证码
   * @param {string} email - 邮箱地址
   * @returns {Promise<ResponseResult<string>>}
   */
  async sendCode(email) {
    return request(`/api/user/sendCode?email=${encodeURIComponent(email)}`, {
      method: 'POST',
    })
  },

  /**
   * 登录或注册
   * @param {string} email - 邮箱地址
   * @param {string} code - 验证码
   * @returns {Promise<ResponseResult<{userInfo: string, token: string}>>}
   */
  async loginOrRegister(email, code) {
    const result = await request('/api/user/loginOrRegister', {
      method: 'POST',
      body: JSON.stringify({
        email,
        inputCode: code,
      }),
    })

    // 登录成功后保存 token
    if (result.status && result.data?.token) {
      setToken(result.data.token)
    }

    return result
  },

  /**
   * 生成题目
   * @param {Object} params - 生成参数
   * @param {string[]} params.tagIds - 标签ID列表（标签名称）
   * @param {string} params.difficulty - 难度："简单"、"中等"、"困难"
   * @param {string} [params.source] - 题目来源/场景："竞赛"、"面试"、"练习"
   * @param {number} [params.questionType] - 题目类型，0=ACM模式，1=OI模式，默认0
   * @param {number} [params.number] - 生成题目数量，默认1
   * @param {number} [params.timeLimit] - 时间限制（毫秒），默认1000
   * @param {number} [params.memoryLimit] - 内存限制（MB），默认256
   * @param {string} [params.additionalRequirements] - 额外要求
   * @param {string} params.userUuid - 用户唯一标识
   * @returns {Promise<ResponseResult<any>>}
   */
  async generateProblem(params) {
    return request('/api/problem/generate', {
      method: 'POST',
      body: JSON.stringify(params),
    })
  },

  /**
   * 获取用户生成的所有题目列表
   * @param {string} userKey - 用户唯一标识（userUuid）
   * @returns {Promise<ResponseResult<any[]>>}
   */
  async getAllProblems(userKey) {
    return request(`/api/problem/delivery/${encodeURIComponent(userKey)}`, {
      method: 'GET',
    })
  },

  /**
   * 获取用户新生成的题目列表
   * @param {string} userKey - 用户唯一标识（userUuid）
   * @returns {Promise<ResponseResult<any[]>>}
   */
  async getNewProblems(userKey) {
    return request(`/api/problem/delivery/new/${encodeURIComponent(userKey)}`, {
      method: 'GET',
    })
  },

  /**
   * 分页查询题目（支持搜索和筛选）
   * @param {string} userKey - 用户唯一标识（userUuid）
   * @param {Object} params - 查询参数
   * @param {string} [params.searchKeyword] - 搜索关键词
   * @param {number} [params.difficulty] - 难度筛选 (0-简单, 1-中等, 2-困难)
   * @param {string[]} [params.tagNames] - 标签筛选列表
   * @param {number} [params.page=1] - 页码
   * @param {number} [params.pageSize=10] - 每页大小
   * @returns {Promise<ResponseResult<ProblemPageResponse>>}
   */
  async getProblemsPaged(userKey, params) {
    return request(`/api/problem/delivery/paged/${encodeURIComponent(userKey)}`, {
      method: 'POST',
      body: JSON.stringify(params),
    })
  },

  /**
   * 运行测试用例
   * @param {Object} params - 运行参数
   * @param {number} params.userId - 用户ID
   * @param {string} params.code - 代码
   * @param {string} params.language - 编程语言
   * @param {number} [params.questionId] - 题目ID
   * @param {string} [params.userInput] - 用户自定义输入
   * @returns {Promise<ResponseResult<string>>}
   */
  async runTestCase(params) {
    return request('/api/submissions/run', {
      method: 'POST',
      body: JSON.stringify(params),
    })
  },

  /**
   * 查询运行测试用例结果
   * @param {string} requestId - 运行任务ID
   * @returns {Promise<ResponseResult<any>>}
   */
  async getRunResult(requestId) {
    return request(`/api/submissions/run/${encodeURIComponent(requestId)}`, {
      method: 'GET',
    })
  },

  /**
   * 提交代码
   * @param {Object} params - 提交参数
   * @param {number} params.userId - 用户ID
   * @param {number} params.questionId - 题目ID
   * @param {string} params.code - 代码
   * @param {string} params.language - 编程语言
   * @param {number} [params.questionVersion] - 题目版本号
   * @param {number} [params.testSetVersion] - 测试用例版本号
   * @param {string} [params.questionSnapshot] - 题目快照（JSON字符串）
   * @param {string} [params.contentHash] - 题目内容哈希
   * @returns {Promise<ResponseResult<{submissionId: number, judgeStatus: string}>>}
   */
  async submitCode(params) {
    return request('/api/submissions', {
      method: 'POST',
      body: JSON.stringify(params),
    })
  },

  /**
   * 查询提交结果
   * @param {number|string} submissionId - 提交ID
   * @returns {Promise<ResponseResult<any>>}
   */
  async getSubmissionResult(submissionId) {
    return request(`/api/submissions/${encodeURIComponent(submissionId)}`, {
      method: 'GET',
    })
  },

  /**
   * 分页查询用户的提交记录
   * @param {Object} params - 查询参数
   * @param {number} params.userId - 用户ID
   * @param {number} [params.pageNum=1] - 页码
   * @param {number} [params.pageSize=6] - 每页条数
   * @param {string} [params.questionTitle] - 题目标题模糊搜索
   * @param {string} [params.language] - 编程语言筛选
   * @param {string} [params.judgeStatus] - 判题状态筛选
   * @returns {Promise<ResponseResult<SubmissionPageResponse>>}
   */
  async getSubmissionHistory(params) {
    const queryParams = new URLSearchParams({
      userId: params.userId,
      pageNum: params.pageNum || 1,
      pageSize: params.pageSize || 6,
    })
    if (params.questionTitle) {
      queryParams.append('questionTitle', params.questionTitle)
    }
    if (params.language) {
      queryParams.append('language', params.language)
    }
    if (params.judgeStatus) {
      queryParams.append('judgeStatus', params.judgeStatus)
    }
    return request(`/api/submissions/page?${queryParams.toString()}`, {
      method: 'GET',
    })
  },
}

// 导出工具方法
export { getToken, setToken, request }

