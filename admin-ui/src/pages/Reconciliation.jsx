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
  { value: 'REFUND_CHANNEL_ONLY', label: '退款-渠道有本地无' },
  { value: 'REFUND_LOCAL_ONLY', label: '退款-本地有渠道无' },
  { value: 'REFUND_AMOUNT_MISMATCH', label: '退款-金额不一致' },
  { value: 'REFUND_STATUS_MISMATCH', label: '退款-状态不一致' }
]

const fen = (v) => (v === null || v === undefined ? '-' : `¥${(v / 100).toFixed(2)}`)

export default function Reconciliation() {
  const [queryDate, setQueryDate] = useState(null)
  const [importList, setImportList] = useState([])
  const [loading, setLoading] = useState(false)

  const [uploadDate, setUploadDate] = useState(null)
  const [fileList, setFileList] = useState([])
  const [uploading, setUploading] = useState(false)

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
      message.warning('请选择微信交易账单 CSV 文件')
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
      width: 220,
      align: 'center',
      fixed: 'right',
      render: (_, row) => (
        <Space size="small" wrap>
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
      render: (_, row) => row.recordType === 'REFUND' ? row.refundSuccessTime || row.refundApplyTime || '-' : row.tradeTime || '-'
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
    { title: '业务单号', dataIndex: 'bizNo', width: 200 },
    { title: '渠道流水号', dataIndex: 'channelSerialNo', width: 200 },
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
      width: 90,
      align: 'center',
      fixed: 'right',
      render: (_, row) =>
        row.status === 'OPEN' ? (
          <Button type="link" size="small" onClick={() => openResolve(row)}>
            标记处理
          </Button>
        ) : (
          '-'
        )
    }
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>对账管理</Typography.Title>

        <Card style={{ marginBottom: 16 }}>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="上传微信交易账单（CSV）后自动完成支付记录与退款记录对账"
            description="支持微信支付商户平台下载的三种交易账单：ALL（全部账单，含支付与退款）、SUCCESS（支付成功账单）、REFUND（退款账单），系统按表头自动识别，无需固定文件名。上传 ALL 一次完成双向对账；也可同一账单日分别上传 SUCCESS 与 REFUND，各自对支付、退款方向对账。同一文件重复上传自动返回原批次（幂等）；同一账单日同种类账单不可重复上传，ALL 与 SUCCESS/REFUND 互斥。"
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
                  accept=".csv"
                  maxCount={1}
                  fileList={fileList}
                  beforeUpload={() => false}
                  onRemove={() => setFileList([])}
                  onChange={({ fileList: list }) => setFileList(list.slice(-1))}
                >
                  <Button>选择 CSV 文件</Button>
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
        width={1200}
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
          scroll={{ x: 1500 }}
          size="small"
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
        />
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
