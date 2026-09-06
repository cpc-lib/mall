<template>
  <div>
    <h2 class="adm-page-title">MQ / 库存异常</h2>
    <div class="adm-card" style="padding:8px 20px 16px">
      <el-table :data="logs" style="width:100%"><el-table-column prop="bizNo" label="bizNo" min-width="190"/><el-table-column prop="operationType" label="类型"/><el-table-column prop="operationStatus" label="状态"/><el-table-column prop="errorMessage" label="错误" min-width="240"/><el-table-column label="操作" width="100"><template slot-scope="s"><el-button size="mini" :disabled="s.row.operationStatus!=='NEED_MANUAL'" @click="replay(s.row)">重放</el-button></template></el-table-column></el-table>
    </div>
  </div>
</template>
<script>
import stockApi from '../api/adminStock'
export default {
  data() { return { logs: [] } },
  created() { this.load() },
  methods: {
    async load() { const r = await stockApi.list(); this.logs = r.data || [] },
    async replay(row) { await stockApi.replay(row.id); this.$message.success('已重放'); await this.load() }
  }
}
</script>
