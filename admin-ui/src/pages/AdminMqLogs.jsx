import { useEffect, useState } from 'react'
import { Button, Card, Input, InputNumber, message, Modal, Space, Table, Tag, Typography } from 'antd'
import stockApi from '@/api/adminStock'

export default function AdminMqLogs() {
  const [logs, setLogs] = useState([])
  const load = () => { stockApi.list().then(r => setLogs(r.data || [])) }
  useEffect(load, [])
  const stockCols = [
    { title: 'bizNo', dataIndex: 'bizNo' }, { title: '类型', dataIndex: 'operationType' },
    { title: '状态', dataIndex: 'operationStatus' }, { title: '错误', dataIndex: 'errorMessage' },
    { title: '操作', render: (_, v) => <Button disabled={v.operationStatus !== 'NEED_MANUAL'} onClick={async () => { await stockApi.replay(v.id); message.success('已重放'); load() }}>重放</Button> }
  ]
  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>MQ / 库存异常</Typography.Title>
    <Card styles={{ body: { padding: '8px 20px 16px' } }}>
      <Table rowKey="id" dataSource={logs} columns={stockCols} />
    </Card>
  </div>
}
