const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request(method, path, data = null, options = {}) {
  const url = `${BASE_URL}${path}`
  const config = {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers
    },
    ...options
  }

  if (data && method !== 'GET') {
    config.body = JSON.stringify(data)
  }

  const response = await fetch(url, config)

  if (!response.ok) {
    const error = new Error(response.statusText || '请求失败')
    error.status = response.status
    try {
      error.data = await response.json()
    } catch {
      // ignore parse error
    }
    throw error
  }

  if (response.status === 204) return null

  return response.json()
}

export const api = {
  get: (path, options) => request('GET', path, null, options),
  post: (path, data, options) => request('POST', path, data, options),
  put: (path, data, options) => request('PUT', path, data, options),
  delete: (path, options) => request('DELETE', path, null, options)
}

// Project APIs
export const projectApi = {
  list: () => api.get('/api/projects'),
  get: (id) => api.get(`/api/projects/${id}`),
  create: (data) => api.post('/api/projects', data),
  update: (id, data) => api.put(`/api/projects/${id}`, data),
  delete: (id) => api.delete(`/api/projects/${id}`)
}

// Requirement APIs
export const requirementApi = {
  list: (projectId) => api.get(`/api/projects/${projectId}/requirements`),
  get: (id) => api.get(`/api/requirements/${id}`),
  create: (projectId, data) => api.post(`/api/projects/${projectId}/requirements`, data),
  update: (projectId, id, data) => api.put(`/api/projects/${projectId}/requirements/${id}`, data),
  review: (id, data) => api.post(`/api/requirements/${id}/review`, data),
  submit: (id) => api.post(`/api/requirements/${id}/submit`),
  approve: (id, reviewerId, comment) => api.post(`/api/requirements/${id}/approve?reviewerId=${encodeURIComponent(reviewerId)}&comment=${encodeURIComponent(comment)}`),
  reject: (id, reviewerId, comment) => api.post(`/api/requirements/${id}/reject?reviewerId=${encodeURIComponent(reviewerId)}&comment=${encodeURIComponent(comment)}`),
  startImplementation: (id) => api.post(`/api/requirements/${id}/start-implementation`)
}

// Bug APIs
export const bugApi = {
  list: (projectId) => api.get(`/api/projects/${projectId}/bugs`),
  get: (id) => api.get(`/api/bugs/${id}`),
  create: (projectId, data) => api.post(`/api/projects/${projectId}/bugs`, data),
  update: (id, data) => api.put(`/api/bugs/${id}`, data),
  assign: (id, assigneeId) => api.post(`/api/bugs/${id}/assign?assigneeId=${encodeURIComponent(assigneeId)}`),
  fix: (id, fixDescription) => api.post(`/api/bugs/${id}/fix?fixDescription=${encodeURIComponent(fixDescription)}`),
  verify: (id) => api.post(`/api/bugs/${id}/verify`),
  close: (id) => api.post(`/api/bugs/${id}/close`),
  reopen: (id) => api.post(`/api/bugs/${id}/reopen`)
}

// Design Proposal APIs
export const designProposalApi = {
  list: (projectId) => api.get(`/api/projects/${projectId}/design-proposals`),
  get: (id) => api.get(`/api/design-proposals/${id}`),
  create: (projectId, data) => api.post(`/api/projects/${projectId}/design-proposals`, data),
  update: (id, data) => api.put(`/api/design-proposals/${id}`, data),
  submit: (id) => api.post(`/api/design-proposals/${id}/submit`),
  review: (id, reviewerId, comment, approved) => api.post(`/api/design-proposals/${id}/review?reviewerId=${encodeURIComponent(reviewerId)}&comment=${encodeURIComponent(comment)}&approved=${approved}`),
  delete: (id) => api.delete(`/api/design-proposals/${id}`)
}

// Test Case APIs
export const testCaseApi = {
  list: (projectId) => api.get(`/api/projects/${projectId}/test-cases`),
  get: (id) => api.get(`/api/test-cases/${id}`),
  create: (projectId, data) => api.post(`/api/projects/${projectId}/test-cases`, data),
  update: (id, data) => api.put(`/api/test-cases/${id}`, data),
  submit: (id) => api.post(`/api/test-cases/${id}/submit`),
  review: (id, reviewerId, comment, approved) => api.post(`/api/test-cases/${id}/review?reviewerId=${encodeURIComponent(reviewerId)}&comment=${encodeURIComponent(comment)}&approved=${approved}`),
  delete: (id) => api.delete(`/api/test-cases/${id}`)
}

// Notification APIs
export const notificationApi = {
  list: (targetClientId) => api.get(`/api/notifications?targetClientId=${encodeURIComponent(targetClientId)}`),
  acknowledge: (id) => api.post(`/api/notifications/${id}/acknowledge`)
}
