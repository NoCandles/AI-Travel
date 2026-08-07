import React, { useEffect, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, Tag, Modal, message, Descriptions } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tripService } from '../../services/trips';

const TripsPage: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>();
  const [detail, setDetail] = useState<any>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const fetchData = () => {
    setLoading(true);
    tripService.list({ page, size: 20, keyword, status: statusFilter })
      .then((res: any) => { setData(res.data.records); setTotal(res.data.total); })
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, [page, statusFilter]);

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除此行程？将级联删除所有天数和景点',
      okType: 'danger',
      onOk: () => tripService.delete(id).then(() => { message.success('已删除'); fetchData(); }),
    });
  };

  const handleDetail = (id: string) => {
    tripService.detail(id).then((res: any) => { setDetail(res.data); setDetailOpen(true); });
  };

  const columns: ColumnsType<any> = [
    { title: '行程名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '目的地', dataIndex: 'destination', width: 100 },
    { title: '用户ID', dataIndex: 'userId', width: 100, ellipsis: true },
    { title: '出行方式', dataIndex: 'travelMode', width: 80 },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (v: string) => {
        const colorMap: Record<string, string> = { GENERATING: 'blue', DRAFT: 'default', SAVED: 'green', ONGOING: 'orange', COMPLETED: 'purple', FAILED: 'red' };
        return <Tag color={colorMap[v] || 'default'}>{v}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作', key: 'action', width: 140,
      render: (_, r: any) => (
        <Space>
          <Button size="small" onClick={() => handleDetail(r.id)}>详情</Button>
          <Button size="small" danger onClick={() => handleDelete(r.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>行程管理</h2>
      <Space style={{ marginBottom: 16 }}>
        <Input placeholder="搜索名称/目的地" value={keyword} onChange={(e) => setKeyword(e.target.value)} style={{ width: 200 }} />
        <Select allowClear placeholder="状态" style={{ width: 120 }} value={statusFilter} onChange={setStatusFilter}>
          <Select.Option value="GENERATING">生成中</Select.Option>
          <Select.Option value="DRAFT">草稿</Select.Option>
          <Select.Option value="SAVED">已保存</Select.Option>
          <Select.Option value="ONGOING">进行中</Select.Option>
          <Select.Option value="COMPLETED">已完成</Select.Option>
        </Select>
        <Button type="primary" onClick={() => { setPage(1); fetchData(); }}>搜索</Button>
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage }} scroll={{ x: 900 }} />
      <Modal open={detailOpen} title="行程详情" footer={null} onCancel={() => setDetailOpen(false)} width={800}>
        {detail && (
          <div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="目的地">{detail.destination}</Descriptions.Item>
              <Descriptions.Item label="状态">{detail.status}</Descriptions.Item>
              <Descriptions.Item label="出行方式">{detail.travelMode}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{detail.createdAt}</Descriptions.Item>
              <Descriptions.Item label="用户ID">{detail.userId}</Descriptions.Item>
            </Descriptions>
            {detail.days?.map((day: any, i: number) => (
              <Card key={day.id} title={`第${day.number}天`} style={{ marginTop: 12 }} size="small">
                {detail.spotsByDay?.[i]?.map((s: any) => (
                  <Tag key={s.id} style={{ margin: 4 }}>{s.name} ({s.category})</Tag>
                ))}
              </Card>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default TripsPage;
