<template>
  <div>
    <h2 class="adm-page-title">对账管理</h2>

      <el-card shadow="never" style="margin-bottom:16px">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
          title="上传微信交易账单（CSV）后自动完成支付记录与退款记录对账"
          description="支持微信支付商户平台下载的三种交易账单：ALL（全部账单，含支付与退款）、SUCCESS（支付成功账单）、REFUND（退款账单），系统按表头自动识别，无需固定文件名。上传 ALL 一次完成双向对账；也可同一账单日分别上传 SUCCESS 与 REFUND，各自对支付、退款方向对账。同一文件重复上传自动返回原批次（幂等）；同一账单日同种类账单不可重复上传，ALL 与 SUCCESS/REFUND 互斥。" />
        <el-form :inline="true">
          <el-form-item label="账单日期" required>
            <el-date-picker v-model="uploadDate" type="date" value-format="yyyy-MM-dd"
              placeholder="选择账单日期" :picker-options="historyPickerOptions" />
          </el-form-item>
          <el-form-item label="账单文件" required>
            <el-upload action="#" :auto-upload="false" :limit="1" accept=".csv"
              :file-list="uploadFileList" :on-change="onFileChange" :on-remove="onFileRemove"
              :on-exceed="onFileExceed">
              <el-button size="small">选择 CSV 文件</el-button>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="uploading" @click="handleUpload">上传并对账</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6"><el-card shadow="never"><div class="metric">导入批次<strong>{{imports.length}}</strong></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="metric">已对账<strong style="color:#67c23a">{{reconciledCount}}</strong></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="metric">对账失败<strong style="color:#f56c6c">{{failedCount}}</strong></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="metric">差异总数<strong style="color:#e6a23c">{{discrepancyTotalCount}}</strong></div></el-card></el-col>
      </el-row>

      <el-card shadow="never">
        <el-form :inline="true">
          <el-form-item label="账单日期">
            <el-date-picker v-model="queryDate" type="date" value-format="yyyy-MM-dd"
              placeholder="按账单日期筛选" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadImports">查询</el-button>
            <el-button @click="queryDate=''">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="imports" border v-loading="loading">
          <el-table-column prop="importNo" label="批次号" width="180" />
          <el-table-column prop="billDate" label="账单日期" width="110" />
          <el-table-column label="账单种类" width="110">
            <template slot-scope="s">
              <el-tag :type="s.row.billKind === 'ALL' ? '' : (s.row.billKind === 'SUCCESS' ? 'success' : 'warning')" size="small">{{s.row.billKindText || s.row.billKind}}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column prop="payRecordCount" label="支付笔数" width="90" align="center" />
          <el-table-column prop="refundRecordCount" label="退款笔数" width="90" align="center" />
          <el-table-column prop="badLineCount" label="坏行" width="70" align="center" />
          <el-table-column prop="matchedCount" label="已匹配" width="80" align="center" />
          <el-table-column label="差异数" width="80" align="center">
            <template slot-scope="s">
              <el-tag :type="s.row.discrepancyCount > 0 ? 'danger' : 'success'" size="small">{{s.row.discrepancyCount || 0}}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template slot-scope="s">
              <el-tooltip :content="s.row.status === 'FAILED' ? (s.row.errorMessage || '') : ''" placement="top" :disabled="s.row.status !== 'FAILED'">
                <el-tag :type="importTagType(s.row.status)" size="small">{{s.row.statusText || s.row.status}}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="导入时间" width="170" />
          <el-table-column label="操作" width="230" align="center" fixed="right">
            <template slot-scope="s">
              <el-button type="text" @click="openRecords(s.row)">账单流水</el-button>
              <el-button type="text" @click="openDiscrepancies(s.row)">差异列表</el-button>
              <el-button type="text" @click="handleReconcile(s.row)">重新对账</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

    <el-drawer :title="'账单流水' + (currentImport ? ' - ' + currentImport.importNo + '（' + currentImport.billDate + '）' : '')"
      :visible.sync="recordVisible" size="1100px" direction="rtl">
      <div style="margin-bottom:12px;padding:0 8px">
        <el-select v-model="recordFilter" clearable placeholder="记录类型" style="width:140px" @change="loadRecords">
          <el-option label="支付" value="PAY" />
          <el-option label="退款" value="REFUND" />
        </el-select>
      </div>
      <div style="padding:0 8px">
      <el-table :data="records" border v-loading="recordLoading" size="small">
        <el-table-column label="类型" width="90">
          <template slot-scope="s"><el-tag :type="s.row.recordType === 'PAY' ? 'success' : 'warning'" size="mini">{{s.row.recordTypeText || s.row.recordType}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="channelSerialNo" label="渠道流水号" min-width="210" show-overflow-tooltip />
        <el-table-column prop="tradeType" label="交易类型" width="120" />
        <el-table-column prop="tradeStatus" label="交易状态" width="110" />
        <el-table-column label="支付金额" width="110">
          <template slot-scope="s">{{s.row.recordType === 'PAY' ? money(s.row.totalAmount) : '-'}}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="110">
          <template slot-scope="s">{{s.row.recordType === 'REFUND' ? money(s.row.refundAmount) : '-'}}</template>
        </el-table-column>
        <el-table-column label="交易/退款时间" width="170">
          <template slot-scope="s">{{s.row.recordType === 'REFUND' ? (s.row.refundSuccessTime || s.row.refundApplyTime || '-') : (s.row.tradeTime || '-')}}</template>
        </el-table-column>
      </el-table>
      </div>
      <div slot="footer" style="text-align:right">
        <el-button @click="recordVisible=false">关闭</el-button>
      </div>
    </el-drawer>

    <el-drawer :title="'差异列表' + (currentImport ? ' - ' + currentImport.importNo + '（' + currentImport.billDate + '）' : '')"
      :visible.sync="discrepancyVisible" size="1200px" direction="rtl">
      <div style="margin-bottom:12px;padding:0 8px">
        <el-select v-model="discFilter.status" clearable placeholder="处理状态" style="width:140px" @change="loadDiscrepancies">
          <el-option label="待处理" value="OPEN" />
          <el-option label="已处理" value="RESOLVED" />
        </el-select>
        <el-select v-model="discFilter.discrepancyType" clearable placeholder="差异类型" style="width:200px;margin-left:8px" @change="loadDiscrepancies">
          <el-option v-for="t in discrepancyTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>
      <div style="padding:0 8px">
      <el-table :data="discrepancies" border v-loading="discLoading" size="small">
        <el-table-column label="差异类型" width="170">
          <template slot-scope="s"><el-tag type="danger" size="mini">{{s.row.discrepancyTypeText || s.row.discrepancyType}}</el-tag></template>
        </el-table-column>
        <el-table-column label="业务类型" width="90">
          <template slot-scope="s"><el-tag :type="s.row.bizType === 'PAY' ? 'success' : 'warning'" size="mini">{{s.row.bizTypeText || s.row.bizType}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="channelSerialNo" label="渠道流水号" min-width="190" show-overflow-tooltip />
        <el-table-column label="渠道金额" width="110"><template slot-scope="s">{{money(s.row.channelAmount)}}</template></el-table-column>
        <el-table-column label="本地金额" width="110"><template slot-scope="s">{{money(s.row.localAmount)}}</template></el-table-column>
        <el-table-column label="渠道状态" width="110"><template slot-scope="s">{{s.row.channelStatus || '-'}}</template></el-table-column>
        <el-table-column label="本地状态" width="110"><template slot-scope="s">{{s.row.localStatus || '-'}}</template></el-table-column>
        <el-table-column label="处理状态" width="100">
          <template slot-scope="s"><el-tag :type="s.row.status === 'OPEN' ? 'warning' : 'success'" size="mini">{{s.row.statusText || s.row.status}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="resolveRemark" label="处理备注" min-width="150" show-overflow-tooltip>
          <template slot-scope="s">{{s.row.resolveRemark || '-'}}</template>
        </el-table-column>
        <el-table-column prop="resolvedBy" label="处理人" width="100"><template slot-scope="s">{{s.row.resolvedBy || '-'}}</template></el-table-column>
        <el-table-column prop="resolvedTime" label="处理时间" width="170"><template slot-scope="s">{{s.row.resolvedTime || '-'}}</template></el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template slot-scope="s">
            <el-button v-if="s.row.status === 'OPEN'" type="text" @click="openResolve(s.row)">标记处理</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <div slot="footer" style="text-align:right">
        <el-button @click="discrepancyVisible=false">关闭</el-button>
      </div>
    </el-drawer>

    <el-dialog title="标记差异已处理" :visible.sync="resolveVisible" width="500px">
      <p v-if="resolveRow">差异类型：<el-tag type="danger" size="mini">{{resolveRow.discrepancyTypeText}}</el-tag>
        业务单号：<strong>{{resolveRow.bizNo}}</strong></p>
      <el-input v-model.trim="resolveRemark" type="textarea" :rows="4" maxlength="500" show-word-limit
        placeholder="请输入差异处理说明（必填），如：已与渠道核对为跨日清算，差异关闭" />
      <span slot="footer">
        <el-button @click="resolveVisible=false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="handleResolve">确认处理</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import api from '../api/reconciliation'

export default {
  name: 'Reconciliation',
  data () {
    return {
      historyPickerOptions: {
        disabledDate (time) { return time.getTime() > Date.now() }
      },
      discrepancyTypes: [
        { value: 'PAY_CHANNEL_ONLY', label: '支付-渠道有本地无' },
        { value: 'PAY_LOCAL_ONLY', label: '支付-本地有渠道无' },
        { value: 'PAY_AMOUNT_MISMATCH', label: '支付-金额不一致' },
        { value: 'PAY_STATUS_MISMATCH', label: '支付-状态不一致' },
        { value: 'REFUND_CHANNEL_ONLY', label: '退款-渠道有本地无' },
        { value: 'REFUND_LOCAL_ONLY', label: '退款-本地有渠道无' },
        { value: 'REFUND_AMOUNT_MISMATCH', label: '退款-金额不一致' },
        { value: 'REFUND_STATUS_MISMATCH', label: '退款-状态不一致' }
      ],
      queryDate: '',
      imports: [],
      loading: false,
      uploadDate: '',
      uploadFile: null,
      uploadFileList: [],
      uploading: false,
      recordVisible: false,
      currentImport: null,
      records: [],
      recordLoading: false,
      recordFilter: '',
      discrepancyVisible: false,
      discrepancies: [],
      discLoading: false,
      discFilter: { status: '', discrepancyType: '' },
      resolveVisible: false,
      resolveRow: null,
      resolveRemark: '',
      resolving: false
    }
  },
  computed: {
    reconciledCount () { return this.imports.filter(i => i.status === 'RECONCILED').length },
    failedCount () { return this.imports.filter(i => i.status === 'FAILED').length },
    discrepancyTotalCount () { return this.imports.reduce((s, i) => s + (i.discrepancyCount || 0), 0) }
  },
  created () { this.loadImports() },
  methods: {
    money (v) { return (v === null || v === undefined) ? '-' : (Number(v) / 100).toFixed(2) },
    importTagType (status) {
      return status === 'RECONCILED' ? 'success' : status === 'FAILED' ? 'danger' : 'info'
    },
    async loadImports () {
      this.loading = true
      try {
        const params = {}
        if (this.queryDate) params.billDate = this.queryDate
        const r = await api.listImports(params)
        this.imports = r.data || []
      } finally {
        this.loading = false
      }
    },
    onFileChange (file, fileList) {
      this.uploadFile = file.raw
      this.uploadFileList = fileList.slice(-1)
    },
    onFileRemove () {
      this.uploadFile = null
      this.uploadFileList = []
    },
    onFileExceed (files) {
      this.uploadFile = files[0]
      this.uploadFileList = [{ name: files[0].name, raw: files[0] }]
    },
    async handleUpload () {
      if (!this.uploadDate) return this.$message.warning('请选择账单日期')
      if (!this.uploadFile) return this.$message.warning('请选择微信交易账单 CSV 文件')
      const formData = new FormData()
      formData.append('file', this.uploadFile)
      formData.append('billDate', this.uploadDate)
      formData.append('billType', 'tradebill')
      this.uploading = true
      try {
        const r = await api.uploadBill(formData)
        const data = r.data || {}
        if (data.status === 'FAILED') {
          this.$message.warning('对账失败：' + (data.errorMessage || '未知原因'))
        } else if (data.discrepancyCount > 0) {
          this.$message.success('上传成功，发现 ' + data.discrepancyCount + ' 笔差异，请查看差异列表')
        } else {
          this.$message.success(r.message || '上传并对账完成，无差异')
        }
        this.uploadFile = null
        this.uploadFileList = []
        this.loadImports()
      } finally {
        this.uploading = false
      }
    },
    openRecords (row) {
      this.currentImport = row
      this.recordFilter = ''
      this.records = []
      this.recordVisible = true
      this.loadRecords()
    },
    async loadRecords () {
      if (!this.currentImport) return
      this.recordLoading = true
      try {
        const params = {}
        if (this.recordFilter) params.recordType = this.recordFilter
        const r = await api.listRecords(this.currentImport.importNo, params)
        this.records = r.data || []
      } finally {
        this.recordLoading = false
      }
    },
    openDiscrepancies (row) {
      this.currentImport = row
      this.discFilter = { status: '', discrepancyType: '' }
      this.discrepancies = []
      this.discrepancyVisible = true
      this.loadDiscrepancies()
    },
    async loadDiscrepancies () {
      if (!this.currentImport) return
      this.discLoading = true
      try {
        const params = {}
        if (this.discFilter.status) params.status = this.discFilter.status
        if (this.discFilter.discrepancyType) params.discrepancyType = this.discFilter.discrepancyType
        const r = await api.listDiscrepancies(this.currentImport.importNo, params)
        this.discrepancies = r.data || []
      } finally {
        this.discLoading = false
      }
    },
    handleReconcile (row) {
      this.$confirm('确定要对批次 ' + row.importNo + '（账单日期 ' + row.billDate + '）重新执行对账吗？已对账批次将直接返回现有结果。', '确认重新对账', {
        confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'
      }).then(async () => {
        const r = await api.reconcile(row.importNo)
        this.$message.success(r.message || '对账完成')
        this.loadImports()
      }).catch(() => {})
    },
    openResolve (row) {
      this.resolveRow = row
      this.resolveRemark = ''
      this.resolveVisible = true
    },
    async handleResolve () {
      if (!this.resolveRemark) return this.$message.warning('请填写处理备注')
      this.resolving = true
      try {
        await api.resolveDiscrepancy(this.resolveRow.id, { resolveRemark: this.resolveRemark })
        this.$message.success('差异单已标记处理')
        this.resolveVisible = false
        this.loadDiscrepancies()
        this.loadImports()
      } finally {
        this.resolving = false
      }
    }
  }
}
</script>

<style scoped>
.metric { text-align: center; color: #666 }
.metric strong { display: block; font-size: 26px; color: #303133; margin-top: 8px }
</style>
