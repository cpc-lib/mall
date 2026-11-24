import { Button, Drawer, Select, Space, Table } from 'antd'

export default function DiscrepancyListDrawer({ open, onClose, currentImport, status, type, onStatusChange, onTypeChange, typeOptions, list, loading, columns, page, size, total, onPageChange }) {
  return (
    <Drawer
      title={`差异列表${currentImport ? ` - ${currentImport.importNo}（${currentImport.billDate}）` : ''}`}
      open={open}
      width={1450}
      onClose={onClose}
      footer={<div style={{ textAlign: 'right' }}><Button onClick={onClose}>关闭</Button></div>}
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="处理状态"
          style={{ width: 140 }}
          allowClear
          value={status || undefined}
          onChange={onStatusChange}
        >
          <Select.Option value="OPEN">待处理</Select.Option>
          <Select.Option value="RESOLVED">已处理</Select.Option>
        </Select>
        <Select
          placeholder="差异类型"
          style={{ width: 200 }}
          allowClear
          value={type || undefined}
          onChange={onTypeChange}
          options={typeOptions}
        />
      </Space>
      <Table
        rowKey="id"
        dataSource={list}
        columns={columns}
        bordered
        loading={loading}
        scroll={{ x: 1950 }}
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
