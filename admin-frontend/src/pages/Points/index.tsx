import React, { useEffect, useState } from 'react';
import { Button, Input, Table, Tabs, Form, InputNumber, message, Modal } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { pointsService } from '../../services/points';

const PointsPage: React.FC = () => {
  const [recordData, setRecordData] = useState<any[]>([]);
  const [recordLoading, setRecordLoading] = useState(false);
  const [recPage, setRecPage] = useState(1);
  const [recTotal, setRecTotal] = useState(0);

  const [signData, setSignData] = useState<any[]>([]);
  const [signLoading, setSignLoading] = useState(false);
  const [signPage, setSignPage] = useState(1);
  const [signTotal, setSignTotal] = useState(0);

  const [configData, setConfigData] = useState<any[]>([]);
  const [manualOpen, setManualOpen] = useState(false);
  const [manualForm] = Form.useForm();
  const [activeTab, setActiveTab] = useState('records');

  const fetchRecords = () => {
    setRecordLoading(true);
    pointsService.records({ page: recPage, size: 20 })
      .then((res: any) => { setRecordData(res.data.records); setRecTotal(res.data.total); })
      .finally(() => setRecordLoading(false));
  };

  const fetchSignLog = () => {
    setSignLoading(true);
    pointsService.signLog({ page: signPage, size: 20 })
      .then((res: any) => { setSignData(res.data.records); setSignTotal(res.data.total); })
      .finally(() => setSignLoading(false));
  };

  const fetchConfig = () => {
    pointsService.config().then((res: any) => setConfigData(res.data));
  };

  useEffect(() => { fetchRecords(); }, [recPage]);
  useEffect(() => { fetchSignLog(); }, [signPage]);
  useEffect(() => { if (activeTab === 'config') fetchConfig(); }, [activeTab]);

  const handleManual = (values: any) => {
    pointsService.manualPoints(values).then(() => {
      message.success('操作成功');
      setManualOpen(false);
      fetchRecords();
    });
  };

  const handleConfigSave = (record: any) => {
    pointsService.updateConfig(record.id, record).then(() => {
      message.success('已保存');
    });
  };

  const recColumns: ColumnsType<any> = [
    { title: '用户ID', dataIndex: 'userId', width: 100, ellipsis: true },
    { title: '变动', dataIndex: 'changeValue', width: 80, render: (v: number) => <span style={{ color: v > 0 ? 'green' : 'red' }}>{v > 0 ? '+' + v : v}</span> },
    { title: '余额', dataIndex: 'currentBalance', width: 80 },
    { title: '类型', dataIndex: 'type', width: 60, render: (v: string) => v === 'earn' ? '赚取' : '消费' },
    { title: '来源', dataIndex: 'source', width: 80 },
    { title: '描述', dataIndex: 'description', width: 150, ellipsis: true },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
  ];

  const signColumns: ColumnsType<any> = [
    { title: '用户ID', dataIndex: 'userId', width: 100, ellipsis: true },
    { title: '签到日期', dataIndex: 'signInDate', width: 120 },
    { title: '获得积分', dataIndex: 'pointsEarned', width: 80 },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
  ];

  const configColumns: ColumnsType<any> = [
    { title: '等级', dataIndex: 'level', width: 80 },
    { title: '称号', dataIndex: 'title', width: 120 },
    { title: '最低积分', dataIndex: 'minPoints', width: 100, render: (_: any, r: any) => (
        <InputNumber size="small" value={r.minPoints} onChange={(v) => { r.minPoints = v; }} style={{ width: 80 }} />
      )},
    { title: '最高积分', dataIndex: 'maxPoints', width: 100, render: (_: any, r: any) => (
        <InputNumber size="small" value={r.maxPoints} onChange={(v) => { r.maxPoints = v; }} style={{ width: 80 }} />
      )},
    { title: '特权', dataIndex: 'privilege', width: 200, render: (_: any, r: any) => (
        <Input size="small" value={r.privilege} onChange={(e) => { r.privilege = e.target.value; }} />
      )},
    {
      title: '操作', width: 80,
      render: (_, r: any) => <Button size="small" type="primary" onClick={() => handleConfigSave(r)}>保存</Button>,
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>积分管理</h2>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'records', label: '积分流水',
          children: (
            <>
              <Button type="primary" onClick={() => setManualOpen(true)} style={{ marginBottom: 16 }}>手动增减积分</Button>
              <Table columns={recColumns} dataSource={recordData} rowKey="id" loading={recordLoading}
                pagination={{ current: recPage, total: recTotal, pageSize: 20, onChange: setRecPage }} scroll={{ x: 800 }} />
            </>
          ),
        },
        {
          key: 'sign-log', label: '签到记录',
          children: (
            <Table columns={signColumns} dataSource={signData} rowKey="id" loading={signLoading}
              pagination={{ current: signPage, total: signTotal, pageSize: 20, onChange: setSignPage }} scroll={{ x: 600 }} />
          ),
        },
        {
          key: 'config', label: '等级配置',
          children: (
            <Table columns={configColumns} dataSource={configData} rowKey="id" pagination={false} />
          ),
        },
      ]} />
      <Modal open={manualOpen} title="手动增减积分" onCancel={() => setManualOpen(false)} onOk={() => manualForm.submit()}>
        <Form form={manualForm} layout="vertical" onFinish={handleManual}>
          <Form.Item name="userId" label="用户ID" rules={[{ required: true }]}>
            <Input placeholder="输入用户ID" />
          </Form.Item>
          <Form.Item name="points" label="积分（正数为加，负数为扣）" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} placeholder="如: 100 或 -50" />
          </Form.Item>
          <Form.Item name="reason" label="原因" rules={[{ required: true }]}>
            <Input.TextArea placeholder="操作原因" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PointsPage;
