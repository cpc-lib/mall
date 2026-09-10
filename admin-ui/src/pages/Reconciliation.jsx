import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Form,
  Input,
  message,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload
} from 'antd'
import {
  getDiscrepancyDrilldown,
  getReconcileSummary,
  listDiscrepancies,
  listImports,
  listRecords,
  reconcileImport,
  resolveDiscrepancy,
  uploadBill
} from '@/api/reconciliation'

const { TextArea } = Input

const IMPORT_STATUS_COLOR = { IMPORTED: 'processing', RECONCILED: 'success', FAILED: 'error' }
const RECORD_TYPE_COLOR = { PAY: 'green', REFUND: 'orange' }
const DISCREPANCY_STATUS_COLOR = { OPEN: 'warning', RESOLVED: 'success' }
const BILL_KIND_COLOR = { ALL: 'geekblue', SUCCESS: 'green', REFUND: 'orange' }

const DISCREPANCY_TYPE_OPTIONS = [
  { value: 'PAY_CHANNEL_ONLY', label: '支付-渠道有本地无' },
  { value: 'PAY_LOCAL_ONLY', label: '支付-本地有渠道无' },
  { value: 'PAY_AMOUNT_MISMATCH', label: '支付-金额不一致' },
  { value: 'PAY_STATUS_MISMATCH', label: '支付-状态不一致' },
  { value: 'PAY_SERIAL_MISMATCH', label: '支付-渠道流水号不一致' },
  { value: 'PAY_BIZ_NO_MISMATCH', label: '支付-业务单号不一致' },
  { value: 'PAY_CHANNEL_DUPLICATE', label: '支付-渠道流水重复' },
  { value: 'PAY_LOCAL_DUPLICATE', label: '支付-平台流水重复' },
  { value: 'REFUND_CHANNEL_ONLY', label: '退款-渠道有本地无' },
  { value: 'REFUND_LOCAL_ONLY', label: '退款-本地有渠道无' },
  { value: 'REFUND_AMOUNT_MISMATCH', label: '退款-金额不一致' },
  { value: 'REFUND_STATUS_MISMATCH', label: '退款-状态不一致' },
  { value: 'REFUND_CHANNEL_DUPLICATE', label: '退款-渠道流水重复' }
]

const fen = (v) => (v === null || v === undefined ? '-' : `¥${(v / 100).toFixed(2)}`)

export default function Reconciliation() {
  const [queryDate, setQueryDate] = useState(null)
  const [importList, setImportList] = useState([])
  const [loading, setLoading] = useState(false)

  const [uploadDate, setUploadDate] = useState(null)
  const [fileList, setFileList] = useState([])
  const [uploading, setUploading] = useState(false)

  const [summaryOpen, setSummaryOpen] = useState(false)
  const [summaryLoading, setSummaryLoading] = useState(false)
  const [summary, setSummary] = useState(null)

  const [recordModalOpen, setRecordModalOpen] = useState(false)
  const [currentImport, setCurrentImport] = useState(null)
  const [recordList, setRecordList] = useState([])
  const [recordLoading, setRecordLoading] = useState(false)
  const [recordType, setRecordType] = useState('')

  const [discModalOpen, setDiscModalOpen] = useState(false)
  const [discList, setDiscList] = useState([])
  const [discLoading, setDiscLoading] = useState(false)
  const [discStatus, setDiscStatus] = useState('')
  const [discType, setDiscType] = useState('')

  const [drillOpen, setDrillOpen] = useState(false)
  const [drillLoading, setDrillLoading] = useState(false)
  const [drillData, setDrillData] = useState(null)

  const [resolveOpen, setResolveOpen] = useState(false)
  const [currentDisc, setCurrentDisc] = useState(null)
  const [resolveRemark, setResolveRemark] = useState('')
  const [resolving, setResolving] = useState(false)

  const loadImports = useCallback(() => {
    setLoading(true)
    const params = {}
    if (queryDate) {
      params.billDate = queryDate.format('YYYY-MM-DD')
    }
    listImports(params)
      .then((res) => {
        setImportList(res?.data || [])
      })
      .finally(() => {
        setLoading(false)
      })
  }, [queryDate])

  useEffect(() => {
    loadImports()
  }, [loadImports])

  const handleUpload = () => {
    if (!uploadDate) {
      message.warning('请选择账单日期')
      return
    }
    if (!fileList || fileList.length === 0) {
      message.warning('请选择微信交易账单 XLSX 文件')
      return
    }
    const formData = new FormData()
    formData.append('file', fileList[0].originFileObj || fileList[0])
    formData.append('billDate', uploadDate.format('YYYY-MM-DD'))
    formData.append('billType', 'tradebill')
    setUploading(true)
    uploadBill(formData)
      .then((res) => {
        const data = res?.data
        if (data?.status === 'FAILED') {
          message.warning(`对账失败：${data.errorMessage || '未知原因'}`)
        } else if (data?.discrepancyCount > 0) {
          message.success(`上传成功，发现 ${data.discrepancyCount} 笔差异，请查看差异列表`)
        } else {
          message.success(res?.message || '上传并对账完成，无差异')
        }
        setFileList([])
        loadImports()
      })
      .finally(() => {
        setUploading(false)
      })
  }

  const openSummary = (row) => {
    setCurrentImport(row)
    setSummaryOpen(true)
    setSummaryLoading(true)
    getReconcileSummary(row.importNo)
      .then((res) => setSummary(res?.data || null))
      .finally(() => setSummaryLoading(false))
  }

  const openRecords = (row) => {
    setCurrentImport(row)
    setRecordType('')
    setRecordModalOpen(true)
  }

  const loadRecords = useCallback(() => {
    if (!currentImport) return
    setRecordLoading(true)
    const params = {}
    if (recordType) {
      params.recordType = recordType
    }
    listRecords(currentImport.importNo, params)
      .then((res) => {
        setRecordList(res?.data || [])
      })
      .finally(() => {
        setRecordLoading(false)
      })
  }, [currentImport, recordType])

  useEffect(() => {
    if (recordModalOpen) {
      loadRecords()
    }
  }, [recordModalOpen, loadRecords])

  const openDisc = (row) => {
    setCurrentImport(row)
    setDiscStatus('')
    setDiscType('')
    setDiscModalOpen(true)
  }

  const loadDisc = useCallback(() => {
    if (!currentImport) return
    setDiscLoading(true)
    const params = {}
    if (discStatus) {
      params.status = discStatus
    }
    if (discType) {
      params.discrepancyType = discType
    }
    listDiscrepancies(currentImport.importNo, params)
      .then((res) => {
        setDiscList(res?.data || [])
      })
      .finally(() => {
        setDiscLoading(false)
      })
  }, [currentImport, discStatus, discType])

  useEffect(() => {
    if (discModalOpen) {
      loadDisc()
    }
  }, [discModalOpen, loadDisc])

  const handleReconcile = (row) => {
    Modal.confirm({
      title: '确认重新对账',
      content: `确定要对批次 ${row.importNo}（账单日期 ${row.billDate}）重新执行对账吗？已对账批次将直接返回现有结果。`,
      okText: '确认',
      cancelText: '取消',
      onOk: () =>
        reconcileImport(row.importNo).then((res) => {
          message.success(res?.message || '对账完成')
          loadImports()
        })
    })
  }

  const openDrilldown = (row) => {
    setCurrentDisc(row)
    setDrillData(null)
    setDrillOpen(true)
    setDrillLoading(true)
    getDiscrepancyDrilldown(row.id)
      .then((res) => setDrillData(res?.data || null))
      .finally(() => setDrillLoading(false))
  }

  const openResolve = (row) => {
    setCurrentDisc(row)
    setResolveRemark('')
    setResolveOpen(true)
  }

  const handleResolve = () => {
    if (!resolveRemark.trim()) {
      message.warning('请填写处理备注')
      return
    }
    setResolving(true)
    resolveDiscrepancy(currentDisc.id, resolveRemark.trim())
      .then(() => {
        message.success('差异单已标记处理')
        setResolveOpen(false)
        loadDisc()
        loadImports()
      })
      .finally(() => {
        setResolving(false)
      })
  }

  const stats = {
    total: importList.length,
    reconciled: importList.filter((i) => i.status === 'RECONCILED').length,
    failed: importList.filter((i) => i.status === 'FAILED').length,
    discrepancies: importList.reduce((s, i) => s + (i.discrepancyCount || 0), 0)
  }

  const summaryRows = summary ? [
    {
      key: 'PAY', subject: '支付成功',
      channelCount: summary.channelPayCount, localCount: summary.localPayCount,
      channelAmount: summary.channelPayAmount, localAmount: summary.localPayAmount
    },
    {
      key: 'REFUND', subject: '退款成功',
      channelCount: summary.channelRefundCount, localCount: summary.localRefundCount,
      channelAmount: summary.channelRefundAmount, localAmount: summary.localRefundAmount
    },
    {
      key: 'NET', subject: '净额（支付-退款）',
      channelCount: '-', localCount: '-',
      channelAmount: summary.channelNetAmount, localAmount: summary.localNetAmount
    }
  ] : []

  const summaryColumns = [
    { title: '核对科目', dataIndex: 'subject', width: 180 },
    { title: '渠道笔数', dataIndex: 'channelCount', width: 110, align: 'right' },
    { title: '平台笔数', dataIndex: 'localCount', width: 110, align: 'right' },
    { title: '渠道金额', dataIndex: 'channelAmount', width: 140, align: 'right', render: fen },
    { title: '平台金额', dataIndex: 'localAmount', width: 140, align: 'right', render: fen },
    {
      title: '金额差（渠道-平台）', width: 170, align: 'right',
      render: (_, row) => fen(Number(row.channelAmount || 0) - Number(row.localAmount || 0))
    }
  ]

  const importColumns = [
    { title: '批次号', dataIndex: 'importNo', width: 180 },
    { title: '账单日期', dataIndex: 'billDate', width: 110 },
    {
      title: '账单种类',
      dataIndex: 'billKind',
      width: 110,
      render: (kind, row) => (
        <Tag color={BILL_KIND_COLOR[kind] || 'default'}>{row.billKindText || kind}</Tag>
      )
    },
    {
      title: '文件名',
      dataIndex: 'fileName',
      width: 200,
      ellipsis: true,
      render: (v) => (
        <Tooltip title={v}>
          <span>{v}</span>
        </Tooltip>
      )
    },
    { title: '支付笔数', dataIndex: 'payRecordCount', width: 90, align: 'center' },
    { title: '退款笔数', dataIndex: 'refundRecordCount', width: 90, align: 'center' },
    { title: '坏行', dataIndex: 'badLineCount', width: 70, align: 'center' },
    { title: '已匹配', dataIndex: 'matchedCount', width: 80, align: 'center' },
    {
      title: '差异数',
      dataIndex: 'discrepancyCount',
      width: 80,
      align: 'center',
      render: (v) => (v > 0 ? <Tag color="red">{v}</Tag> : <Tag color="green">0</Tag>)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status, row) => (
        <Tooltip title={status === 'FAILED' ? row.errorMessage : ''}>
          <Tag color={IMPORT_STATUS_COLOR[status] || 'default'}>{row.statusText || status}</Tag>
        </Tooltip>
      )
    },
    { title: '导入时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 300,
      align: 'center',
      fixed: 'right',
      render: (_, row) => (
        <Space size="small" wrap>
          <Button type="link" size="small" onClick={() => openSummary(row)}>
            账账汇总
          </Button>
          <Button type="link" size="small" onClick={() => openRecords(row)}>
            账单流水
          </Button>
          <Button type="link" size="small" onClick={() => openDisc(row)}>
            差异列表
          </Button>
          <Button type="link" size="small" onClick={() => handleReconcile(row)}>
            重新对账
          </Button>
        </Space>
      )
    }
  ]

  const recordColumns = [
    {
      title: '类型',
      dataIndex: 'recordType',
      width: 90,
      render: (t, row) => <Tag color={RECORD_TYPE_COLOR[t] || 'default'}>{row.recordTypeText || t}</Tag>
    },
    { title: '业务单号', dataIndex: 'bizNo', width: 200 },
    { title: '渠道流水号', dataIndex: 'channelSerialNo', width: 220 },
    { title: '交易类型', dataIndex: 'tradeType', width: 120 },
    { title: '交易状态', dataIndex: 'tradeStatus', width: 110 },
    {
      title: '支付金额',
      dataIndex: 'totalAmount',
      width: 110,
      render: (v, row) => (row.recordType === 'PAY' ? fen(v) : '-')
    },
    {
      title: '退款金额',
      dataIndex: 'refundAmount',
      width: 110,
      render: (v, row) => (row.recordType === 'REFUND' ? fen(v) : '-')
    },
    {
      title: '交易/退款时间',
      width: 170,
      render: (_, row) => row.recordType === 'REFUND'
        ? row.refundSuccessTime || row.refundApplyTime || row.tradeTime || '-'
        : row.tradeTime || '-'
    }
  ]

  const discColumns = [
    {
      title: '差异类型',
      dataIndex: 'discrepancyType',
      width: 170,
      render: (t, row) => <Tag color="red">{row.discrepancyTypeText || t}</Tag>
    },
    {
      title: '业务类型',
      dataIndex: 'bizType',
      width: 90,
      render: (t, row) => <Tag color={RECORD_TYPE_COLOR[t] || 'default'}>{row.bizTypeText || t}</Tag>
    },
    { title: '渠道业务单号', dataIndex: 'bizNo', width: 190 },
    { title: '渠道流水号', dataIndex: 'channelSerialNo', width: 190 },
    { title: '平台业务单号', dataIndex: 'localBizNo', width: 190, render: (v) => v || '-' },
    { title: '平台账本单号', dataIndex: 'localLedgerNo', width: 190, render: (v) => v || '-' },
    { title: '平台渠道流水', dataIndex: 'localSerialNo', width: 190, render: (v) => v || '-' },
    { title: '渠道金额', dataIndex: 'channelAmount', width: 110, render: fen },
    { title: '本地金额', dataIndex: 'localAmount', width: 110, render: fen },
    { title: '渠道状态', dataIndex: 'channelStatus', width: 110, render: (v) => v || '-' },
    { title: '本地状态', dataIndex: 'localStatus', width: 110, render: (v) => v || '-' },
    {
      title: '处理状态',
      dataIndex: 'status',
      width: 100,
      render: (s, row) => <Tag color={DISCREPANCY_STATUS_COLOR[s] || 'default'}>{row.statusText || s}</Tag>
    },
    { title: '处理备注', dataIndex: 'resolveRemark', width: 160, ellipsis: true, render: (v) => v || '-' },
    { title: '处理人', dataIndex: 'resolvedBy', width: 100, render: (v) => v || '-' },
    { title: '处理时间', dataIndex: 'resolvedTime', width: 170, render: (v) => v || '-' },
    {
      title: '操作',
      width: 160,
      align: 'center',
      fixed: 'right',
      render: (_, row) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openDrilldown(row)}>
            核验下钻
          </Button>
          {row.status === 'OPEN' ? (
            <Button type="link" size="small" onClick={() => openResolve(row)}>
              标记处理
            </Button>
          ) : null}
        </Space>
      )
    }
  ]

  const verificationColumns = [
    { title: '核验字段', dataIndex: 'field', width: 130 },
    { title: '渠道账', dataIndex: 'channelValue', render: (v) => v || '-' },
    { title: '平台账', dataIndex: 'localValue', render: (v) => v || '-' },
    {
      title: '核验结果', dataIndex: 'matched', width: 120, align: 'center',
      render: (matched) => matched === true
        ? <Tag color="green">一致</Tag>
        : matched === false
          ? <Tag color="red">不一致</Tag>
          : <Tag>无法直接核验</Tag>
    }
  ]

  const localLedgerColumns = [
    { title: '账本类型', dataIndex: 'ledgerType', width: 90 },
    { title: '平台账本单号', dataIndex: 'ledgerNo', width: 190 },
    { title: '业务单号', dataIndex: 'bizNo', width: 190 },
    { title: '关联订单号', dataIndex: 'orderNo', width: 190 },
    { title: '支付渠道', dataIndex: 'channel', width: 100, render: (v) => v || '-' },
    { title: '平台渠道流水', dataIndex: 'channelSerialNo', width: 190, render: (v) => v || '-' },
    { title: '金额', dataIndex: 'amount', width: 110, render: fen },
    { title: '状态', dataIndex: 'status', width: 110 },
    { title: '成功时间', dataIndex: 'occurredTime', width: 170, render: (v) => v || '-' },
    { title: '来源支付单', dataIndex: 'sourcePaymentNo', width: 190, render: (v) => v || '-' }
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>账账核对</Typography.Title>

        <Card style={{ marginBottom: 16 }}>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="渠道账 vs 平台交易账：逐笔核对 + 双向扫描 + 总账平衡"
            description="渠道账来自微信交易账单；平台账以 t_payment_order 和 t_refund_order 为权威。支付优先按微信订单号与平台 channelOrderNo 逐笔匹配，订单号只作辅助，避免 1:N 支付尝试被折叠；退款按商户退款单号核对。日切按支付成功时间/退款成功时间统计，并同时核对支付总额、退款总额和净额。"
          />
          <Space wrap size="large">
            <Form layout="inline">
              <Form.Item label="账单日期" required>
                <DatePicker
                  value={uploadDate}
                  onChange={(d) => setUploadDate(d)}
                  disabledDate={(current) => current && current.isAfter(new Date(), 'day')}
                  allowClear={false}
                />
              </Form.Item>
              <Form.Item label="账单文件" required>
                <Upload
                  accept=".xlsx"
                  maxCount={1}
                  fileList={fileList}
                  beforeUpload={(file) => {
                    if (!file.name?.toLowerCase().endsWith('.xlsx')) {
                      message.error('仅支持微信交易账单 XLSX 文件（.xlsx）')
                      return Upload.LIST_IGNORE
                    }
                    return false
                  }}
                  onRemove={() => setFileList([])}
                  onChange={({ fileList: list }) => setFileList(list.slice(-1))}
                >
                  <Button>选择 XLSX 文件</Button>
                </Upload>
              </Form.Item>
              <Form.Item>
                <Button type="primary" loading={uploading} onClick={handleUpload}>
                  上传并对账
                </Button>
              </Form.Item>
            </Form>
          </Space>
        </Card>

        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={6}>
            <Card>
              <Statistic title="导入批次总数" value={stats.total} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="已对账批次" value={stats.reconciled} valueStyle={{ color: '#3f8600' }} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="对账失败批次" value={stats.failed} valueStyle={{ color: '#cf1322' }} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="差异总数" value={stats.discrepancies} valueStyle={{ color: '#fa8c16' }} />
            </Card>
          </Col>
        </Row>

        <Card style={{ marginBottom: 16 }}>
          <Space style={{ marginBottom: 16 }}>
            <span>账单日期：</span>
            <DatePicker
              value={queryDate}
              onChange={(d) => setQueryDate(d)}
              allowClear
              placeholder="按账单日期筛选"
            />
            <Button
              type="primary"
              onClick={() => {
                loadImports()
              }}
            >
              查询
            </Button>
            <Button
              onClick={() => {
                setQueryDate(null)
              }}
            >
              重置
            </Button>
          </Space>
          <Table
            rowKey="id"
            dataSource={importList}
            columns={importColumns}
            bordered
            loading={loading}
            scroll={{ x: 1500 }}
            size="middle"
            pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
          />
        </Card>

      <Drawer
        title={`账账核对汇总${currentImport ? ` - ${currentImport.importNo}（${currentImport.billDate}）` : ''}`}
        open={summaryOpen}
        width={900}
        onClose={() => setSummaryOpen(false)}
        footer={<div style={{ textAlign: 'right' }}><Button onClick={() => setSummaryOpen(false)}>关闭</Button></div>}
      >
        <Card loading={summaryLoading} bordered={false}>
          {summary && <>
            <Alert
              type={summary.balanced ? 'success' : 'warning'}
              showIcon
              style={{ marginBottom: 16 }}
              message={summary.balanced ? '账账平衡' : '账账不平衡，请处理差异'}
              description={`净差额（渠道-平台）：${fen(summary.netDifference)}；待处理差异：${summary.openDiscrepancyCount || 0} 笔。账单种类 ${summary.billKind || '-'}，渠道 ${summary.channelCode || '-' }。`}
            />
            <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
              <Col span={8}><Card size="small"><Statistic title="渠道净额" value={Number(summary.channelNetAmount || 0) / 100} precision={2} prefix="¥" /></Card></Col>
              <Col span={8}><Card size="small"><Statistic title="平台净额" value={Number(summary.localNetAmount || 0) / 100} precision={2} prefix="¥" /></Card></Col>
              <Col span={8}><Card size="small"><Statistic title="待处理差异" value={summary.openDiscrepancyCount || 0} suffix="笔" /></Card></Col>
            </Row>
            <Table rowKey="key" dataSource={summaryRows} columns={summaryColumns} pagination={false} bordered size="middle" />
            <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
              平账条件：支付笔数/金额一致、退款成功笔数/金额一致、净额一致，并且不存在 OPEN 差异单。当前净额口径暂不包含渠道手续费；手续费属于后续资金账单核对范围。
            </Typography.Paragraph>
          </>}
        </Card>
      </Drawer>

      <Drawer
        title={`账单流水${currentImport ? ` - ${currentImport.importNo}（${currentImport.billDate}）` : ''}`}
        open={recordModalOpen}
        width={1100}
        onClose={() => setRecordModalOpen(false)}
        footer={<div style={{ textAlign: 'right' }}><Button onClick={() => setRecordModalOpen(false)}>关闭</Button></div>}
      >
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="记录类型"
            style={{ width: 140 }}
            allowClear
            value={recordType || undefined}
            onChange={(val) => setRecordType(val || '')}
          >
            <Select.Option value="PAY">支付</Select.Option>
            <Select.Option value="REFUND">退款</Select.Option>
          </Select>
        </Space>
        <Table
          rowKey="id"
          dataSource={recordList}
          columns={recordColumns}
          bordered
          loading={recordLoading}
          scroll={{ x: 1100 }}
          size="small"
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
        />
      </Drawer>

      <Drawer
        title={`差异列表${currentImport ? ` - ${currentImport.importNo}（${currentImport.billDate}）` : ''}`}
        open={discModalOpen}
        width={1450}
        onClose={() => setDiscModalOpen(false)}
        footer={<div style={{ textAlign: 'right' }}><Button onClick={() => setDiscModalOpen(false)}>关闭</Button></div>}
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="处理状态"
            style={{ width: 140 }}
            allowClear
            value={discStatus || undefined}
            onChange={(val) => setDiscStatus(val || '')}
          >
            <Select.Option value="OPEN">待处理</Select.Option>
            <Select.Option value="RESOLVED">已处理</Select.Option>
          </Select>
          <Select
            placeholder="差异类型"
            style={{ width: 200 }}
            allowClear
            value={discType || undefined}
            onChange={(val) => setDiscType(val || '')}
            options={DISCREPANCY_TYPE_OPTIONS}
          />
        </Space>
        <Table
          rowKey="id"
          dataSource={discList}
          columns={discColumns}
          bordered
          loading={discLoading}
          scroll={{ x: 1950 }}
          size="small"
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
        />
      </Drawer>

      <Drawer
        title={`核验下钻${currentDisc ? ` - ${currentDisc.discrepancyTypeText || currentDisc.discrepancyType}` : ''}`}
        open={drillOpen}
        width={1200}
        onClose={() => setDrillOpen(false)}
        footer={<div style={{ textAlign: 'right' }}><Button onClick={() => setDrillOpen(false)}>关闭</Button></div>}
      >
        <Card loading={drillLoading} bordered={false}>
          {drillData && <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="渠道账与平台账本双边核验"
              description={`差异类型：${drillData.discrepancy?.discrepancyTypeText || '-'}；渠道业务单号：${drillData.discrepancy?.bizNo || '-'}；平台账本单号：${drillData.discrepancy?.localLedgerNo || '-'}`}
            />

            <Typography.Title level={5}>逐字段核验</Typography.Title>
            <Table
              rowKey="field"
              dataSource={drillData.verificationItems || []}
              columns={verificationColumns}
              pagination={false}
              bordered
              size="small"
              style={{ marginBottom: 24 }}
            />

            <Typography.Title level={5}>渠道账原始记录</Typography.Title>
            {(drillData.channelRecords || []).length > 0 ? <>
              <Table
                rowKey="id"
                dataSource={drillData.channelRecords || []}
                columns={recordColumns}
                pagination={false}
                bordered
                size="small"
                scroll={{ x: 1100 }}
                style={{ marginBottom: 12 }}
              />
              {(drillData.channelRecords || []).map((record) => record.rawLine ? (
                <Card key={`raw-${record.id}`} size="small" title={`原始账单行 #${record.id}`} style={{ marginBottom: 12 }}>
                  <Typography.Paragraph copyable={{ text: record.rawLine }} style={{ marginBottom: 0 }}>
                    <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: 0 }}>{record.rawLine}</pre>
                  </Typography.Paragraph>
                </Card>
              ) : null)}
            </> : <Alert type="warning" showIcon message="渠道侧没有找到对应账单记录" style={{ marginBottom: 24 }} />}

            <Typography.Title level={5}>平台账本候选记录</Typography.Title>
            {(drillData.localLedgers || []).length > 0 ? (
              <Table
                rowKey="ledgerNo"
                dataSource={drillData.localLedgers || []}
                columns={localLedgerColumns}
                pagination={false}
                bordered
                size="small"
                scroll={{ x: 1500 }}
              />
            ) : <Alert type="warning" showIcon message="平台侧没有找到对应交易账本记录" />}
          </>}
        </Card>
      </Drawer>

      <Modal
        title="标记差异已处理"
        open={resolveOpen}
        width={500}
        onCancel={() => setResolveOpen(false)}
        onOk={handleResolve}
        confirmLoading={resolving}
        okText="确认处理"
        cancelText="取消"
      >
        {currentDisc && (
          <div style={{ marginBottom: 12 }}>
            <p>
              差异类型：<Tag color="red">{currentDisc.discrepancyTypeText}</Tag>
              业务单号：<strong>{currentDisc.bizNo}</strong>
            </p>
          </div>
        )}
        <TextArea
          rows={4}
          value={resolveRemark}
          onChange={(e) => setResolveRemark(e.target.value)}
          placeholder="请输入差异处理说明（必填），如：已与渠道核对为跨日清算，差异关闭"
          maxLength={500}
        />
      </Modal>
    </div>
  )
}
