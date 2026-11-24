import { Alert, Button, Card, Drawer, Table, Typography } from 'antd'

export default function DiscrepancyDrilldownDrawer({ open, onClose, currentDisc, loading, drillData, verificationColumns, recordColumns, localLedgerColumns }) {
  return (
    <Drawer
      title={`核验下钻${currentDisc ? ` - ${currentDisc.discrepancyTypeText || currentDisc.discrepancyType}` : ''}`}
      open={open}
      width={1200}
      onClose={onClose}
      footer={<div style={{ textAlign: 'right' }}><Button onClick={onClose}>关闭</Button></div>}
    >
      <Card loading={loading} bordered={false}>
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
  )
}
