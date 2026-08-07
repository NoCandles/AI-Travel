import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Select, Space, Table, Tabs, Modal, message, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { systemService } from '../../services/system';

const SystemPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('admins');

  // ===== 管理员 =====
  const [adminData, setAdminData] = useState<any[]>([]);
  const [adminModalOpen, setAdminModalOpen] = useState(false);
  const [adminForm] = Form.useForm();

  // ===== 日志 =====
  const [logData, setLogData] = useState<any[]>([]);
  const [logLoading, setLogLoading] = useState(false);
  const [logPage, setLogPage] = useState(1);
  const [logTotal, setLogTotal] = useState(0);

  // ===== 公告 =====
  const [annData, setAnnData] = useState<any[]>([]);
  const [annLoading, setAnnLoading] = useState(false);
  const [annPage, setAnnPage] = useState(1);
  const [annTotal, setAnnTotal] = useState(0);
  const [annModalOpen, setAnnModalOpen] = useState(false);
  const [annForm] = Form.useForm();

  // ===== 敏感词 =====
  const [swData, setSwData] = useState<any[]>([]);
  const [swLoading, setSwLoading] = useState(false);
  const [swPage, setSwPage] = useState(1);
  const [swTotal, setSwTotal] = useState(0);
  const [swModalOpen, setSwModalOpen] = useState(false);
  const [swEditId, setSwEditId] = useState<number | null>(null);
  const [swForm] = Form.useForm();

  const fetchAdmins = () => systemService.listAdmins().then((res: any) => setAdminData(res.data));

  const fetchLogs = () => {
    setLogLoading(true);
    systemService.listLogs({ page: logPage, size: 20 })
      .then((res: any) => { setLogData(res.data.records); setLogTotal(res.data.total); })
      .finally(() => setLogLoading(false));
  };

  const fetchAnnouncements = () => {
    setAnnLoading(true);
    systemService.listAnnouncements({ page: annPage, size: 20 })
      .then((res: any) => { setAnnData(res.data.records); setAnnTotal(res.data.total); })
      .finally(() => setAnnLoading(false));
  };

  const fetchSensitiveWords = () => {
    setSwLoading(true);
    systemService.listSensitiveWords({ page: swPage, size: 20 })
      .then((res: any) => { setSwData(res.data.records); setSwTotal(res.data.total); })
      .finally(() => setSwLoading(false));
  };

  useEffect(() => { fetchAdmins(); }, []);
  useEffect(() => { fetchLogs(); }, [logPage]);
  useEffect(() => { fetchAnnouncements(); }, [annPage]);
  useEffect(() => { fetchSensitiveWords(); }, [swPage]);

  // ===== 管理员操作 =====
  const handleCreateAdmin = (values: any) => {
    systemService.createAdmin(values).then(() => {
      message.success('创建成功');
      setAdminModalOpen(false);
      adminForm.resetFields();
      fetchAdmins();
    });
  };

  const handleUpdateAdminStatus = (id: string, status: number) => {
    systemService.updateAdmin(id, { status }).then(() => { message.success('已更新'); fetchAdmins(); });
  };

  // ===== 公告操作 =====
  const handleCreateAnnouncement = (values: any) => {
    systemService.createAnnouncement(values).then(() => {
      message.success('发布成功');
      setAnnModalOpen(false);
      annForm.resetFields();
      fetchAnnouncements();
    });
  };

  const handleDeleteAnnouncement = (id: string) => {
    Modal.confirm({
      title: '确认删除？',
      onOk: () => systemService.deleteAnnouncement(id).then(() => { message.success('已删除'); fetchAnnouncements(); }),
    });
  };

  // ===== 敏感词操作 =====
  const handleOpenSwAdd = () => {
    setSwEditId(null);
    swForm.resetFields();
    setSwModalOpen(true);
  };

  const handleOpenSwEdit = (record: any) => {
    setSwEditId(record.id);
    swForm.setFieldsValue(record);
    setSwModalOpen(true);
  };

  const handleSwSave = (values: any) => {
    if (swEditId) {
      systemService.updateSensitiveWord(swEditId, values).then(() => {
        message.success('更新成功');
        setSwModalOpen(false);
        fetchSensitiveWords();
      });
    } else {
      systemService.createSensitiveWord(values).then(() => {
        message.success('添加成功');
        setSwModalOpen(false);
        fetchSensitiveWords();
      });
    }
  };

  const handleDeleteSensitiveWord = (id: number) => {
    Modal.confirm({
      title: '确认删除？',
      content: '删除后小程序发布时不再拦截该词',
      onOk: () => systemService.deleteSensitiveWord(id).then(() => { message.success('已删除'); fetchSensitiveWords(); }),
    });
  };

  // ===== 表格列定义 =====
  const adminColumns: ColumnsType<any> = [
    { title: '账号', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '角色', dataIndex: 'role', width: 120, render: (v: string) => <Tag color={v === 'super_admin' ? 'red' : 'blue'}>{v}</Tag> },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: number) => v === 1 ? <Tag color="green">正常</Tag> : <Tag color="red">禁用</Tag> },
    { title: '最后登录', dataIndex: 'lastLogin', width: 170 },
    {
      title: '操作', width: 160,
      render: (_, r: any) => (
        <Space>
          {r.status === 1 ? (
            <Button size="small" danger onClick={() => handleUpdateAdminStatus(r.id, 0)}>禁用</Button>
          ) : (
            <Button size="small" onClick={() => handleUpdateAdminStatus(r.id, 1)}>启用</Button>
          )}
        </Space>
      ),
    },
  ];

  const logColumns: ColumnsType<any> = [
    { title: '操作人', dataIndex: 'adminName', width: 100 },
    { title: '模块', dataIndex: 'module', width: 80 },
    { title: '动作', dataIndex: 'action', width: 80 },
    { title: '目标类型', dataIndex: 'targetType', width: 80 },
    { title: '目标ID', dataIndex: 'targetId', width: 100, ellipsis: true },
    { title: '详情', dataIndex: 'detail', width: 200, ellipsis: true, render: (v: any) => v ? JSON.stringify(v) : '-' },
    { title: 'IP', dataIndex: 'ip', width: 120 },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
  ];

  const annColumns: ColumnsType<any> = [
    { title: '标题', dataIndex: 'title', width: 200 },
    { title: '级别', dataIndex: 'level', width: 80, render: (v: string) => {
        const colorMap: Record<string, string> = { info: 'blue', warning: 'orange', important: 'red' };
        return <Tag color={colorMap[v] || 'blue'}>{v}</Tag>;
      }},
    { title: '状态', dataIndex: 'status', width: 80, render: (v: number) => v === 1 ? <Tag color="green">已发布</Tag> : <Tag>已下架</Tag> },
    { title: '发布时间', dataIndex: 'publishAt', width: 170 },
    {
      title: '操作', width: 80,
      render: (_, r: any) => <Button size="small" danger onClick={() => handleDeleteAnnouncement(r.id)}>删除</Button>,
    },
  ];

  const swColumns: ColumnsType<any> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '敏感词', dataIndex: 'word', width: 200 },
    {
      title: '级别', dataIndex: 'level', width: 80,
      render: (v: number) => v === 2 ? <Tag color="red">严重</Tag> : <Tag color="orange">普通</Tag>,
    },
    { title: '添加时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作', width: 140,
      render: (_, r: any) => (
        <Space>
          <Button size="small" onClick={() => handleOpenSwEdit(r)}>编辑</Button>
          <Button size="small" danger onClick={() => handleDeleteSensitiveWord(r.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>系统设置</h2>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'admins', label: '管理员',
          children: (
            <>
              <Button type="primary" onClick={() => setAdminModalOpen(true)} style={{ marginBottom: 16 }}>
                添加管理员
              </Button>
              <Table columns={adminColumns} dataSource={adminData} rowKey="id" pagination={false} />
              <Modal open={adminModalOpen} title="添加管理员" onCancel={() => setAdminModalOpen(false)} onOk={() => adminForm.submit()}>
                <Form form={adminForm} layout="vertical" onFinish={handleCreateAdmin}>
                  <Form.Item name="username" label="账号" rules={[{ required: true }]}>
                    <Input placeholder="登录账号" />
                  </Form.Item>
                  <Form.Item name="password" label="密码" rules={[{ required: true }]}>
                    <Input.Password placeholder="登录密码" />
                  </Form.Item>
                  <Form.Item name="nickname" label="昵称">
                    <Input placeholder="显示名称" />
                  </Form.Item>
                </Form>
              </Modal>
            </>
          ),
        },
        {
          key: 'logs', label: '操作日志',
          children: (
            <Table columns={logColumns} dataSource={logData} rowKey="id" loading={logLoading}
              pagination={{ current: logPage, total: logTotal, pageSize: 20, onChange: setLogPage }} scroll={{ x: 900 }} />
          ),
        },
        {
          key: 'announcements', label: '公告管理',
          children: (
            <>
              <Button type="primary" onClick={() => setAnnModalOpen(true)} style={{ marginBottom: 16 }}>
                发布公告
              </Button>
              <Table columns={annColumns} dataSource={annData} rowKey="id" loading={annLoading}
                pagination={{ current: annPage, total: annTotal, pageSize: 20, onChange: setAnnPage }} />
              <Modal open={annModalOpen} title="发布公告" onCancel={() => setAnnModalOpen(false)} onOk={() => annForm.submit()}>
                <Form form={annForm} layout="vertical" onFinish={handleCreateAnnouncement}>
                  <Form.Item name="title" label="标题" rules={[{ required: true }]}>
                    <Input placeholder="公告标题" />
                  </Form.Item>
                  <Form.Item name="content" label="内容" rules={[{ required: true }]}>
                    <Input.TextArea rows={4} placeholder="公告内容" />
                  </Form.Item>
                  <Form.Item name="level" label="级别" initialValue="info">
                    <Select>
                      <Select.Option value="info">普通</Select.Option>
                      <Select.Option value="warning">警告</Select.Option>
                      <Select.Option value="important">重要</Select.Option>
                    </Select>
                  </Form.Item>
                </Form>
              </Modal>
            </>
          ),
        },
        {
          key: 'sensitive-words', label: '敏感词管理',
          children: (
            <>
              <Button type="primary" onClick={handleOpenSwAdd} style={{ marginBottom: 16 }}>
                添加敏感词
              </Button>
              <Table columns={swColumns} dataSource={swData} rowKey="id" loading={swLoading}
                pagination={{ current: swPage, total: swTotal, pageSize: 20, onChange: setSwPage }} />
              <Modal
                open={swModalOpen}
                title={swEditId ? '编辑敏感词' : '添加敏感词'}
                onCancel={() => setSwModalOpen(false)}
                onOk={() => swForm.submit()}
              >
                <Form form={swForm} layout="vertical" onFinish={handleSwSave}>
                  <Form.Item name="word" label="敏感词" rules={[{ required: true, message: '请输入敏感词' }]}>
                    <Input placeholder="请输入敏感词" />
                  </Form.Item>
                  <Form.Item name="level" label="级别" initialValue={1}>
                    <Select>
                      <Select.Option value={1}>普通</Select.Option>
                      <Select.Option value={2}>严重</Select.Option>
                    </Select>
                  </Form.Item>
                </Form>
              </Modal>
            </>
          ),
        },
      ]} />
    </div>
  );
};

export default SystemPage;
