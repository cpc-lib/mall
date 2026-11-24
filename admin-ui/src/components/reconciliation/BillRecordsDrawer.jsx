import { Button, Drawer, Select, Space, Table } from 'antd'

export default function BillRecordsDrawer({ open, onClose, currentImport, recordType, onRecordTypeChange, list, loading, columns, page, size, total, onPageChange }) {
  return (
    <Drawer
      title={`账单流水${currentImport ? ` - ${currentImport.importNo}（${currentImport.billDate}）` : ''}`}
      open={open}
      width={1100}
      onClose={onClose}
      footer={<div style={{ textAlign: 'right' }}><Button onClick={onClose}>关闭</Button></div>}
    >
      <Space style={{ marginBottom: 16 }}>
        <Select
          placeholder="记录类型"
          style={{ width: 140 }}
          allowClear
          value={recordType || undefined}
          onChange={onRecordTypeChange}
        >
          <Select.Option value="PAY">支付</Select.Option>
          <Select.Option value="REFUND">退款</Select.Option>
        </Select>
      </Space>
      <Table
        rowKey="id"
        dataSource={list}
        columns={columns}
        bordered
        loading={loading}
        scroll={{ x: 1100 }}
        size="small"
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (t) => `共 ${t} 条`,
          onChange: onPageChange
        }}
      />
    </Drawer>
  )
}
