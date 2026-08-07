import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import { UserOutlined, FileProtectOutlined, CommentOutlined, RiseOutlined } from '@ant-design/icons';
import { dashboardService } from '../../services/dashboard';

const DashboardPage: React.FC = () => {
  const [data, setData] = useState<any>({});
  const [trends, setTrends] = useState<any[]>([]);

  useEffect(() => {
    dashboardService.overview().then((res: any) => setData(res.data));
    dashboardService.trends().then((res: any) => setTrends(res.data));
  }, []);

  const cards = [
    { title: '总用户数', value: data.totalUsers, icon: <UserOutlined />, color: '#1677ff' },
    { title: '今日新增', value: data.todayNewUsers, icon: <RiseOutlined />, color: '#52c41a' },
    { title: '总行程数', value: data.totalTrips, icon: <FileProtectOutlined />, color: '#722ed1' },
    { title: '总发布数', value: data.totalPublished, icon: <FileProtectOutlined />, color: '#fa8c16' },
    { title: '总评论数', value: data.totalComments, icon: <CommentOutlined />, color: '#eb2f96' },
    { title: '今日活跃', value: data.todayActiveUsers, icon: <UserOutlined />, color: '#13c2c2' },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 24 }}>数据仪表盘</h2>
      <Row gutter={[16, 16]}>
        {cards.map((c, i) => (
          <Col span={8} key={i}>
            <Card>
              <Statistic title={c.title} value={c.value} prefix={c.icon} valueStyle={{ color: c.color }} />
            </Card>
          </Col>
        ))}
      </Row>
      {trends.length > 0 && (
        <Card title="近30天趋势" style={{ marginTop: 24 }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead>
                <tr style={{ background: '#fafafa' }}>
                  <th style={{ padding: 8, border: '1px solid #f0f0f0' }}>日期</th>
                  <th style={{ padding: 8, border: '1px solid #f0f0f0' }}>新增用户</th>
                  <th style={{ padding: 8, border: '1px solid #f0f0f0' }}>新增行程</th>
                  <th style={{ padding: 8, border: '1px solid #f0f0f0' }}>发布</th>
                  <th style={{ padding: 8, border: '1px solid #f0f0f0' }}>评论</th>
                </tr>
              </thead>
              <tbody>
                {[...trends].reverse().map((t: any) => (
                  <tr key={t.date}>
                    <td style={{ padding: 6, border: '1px solid #f0f0f0', textAlign: 'center' }}>{t.date}</td>
                    <td style={{ padding: 6, border: '1px solid #f0f0f0', textAlign: 'center' }}>{t.newUsers}</td>
                    <td style={{ padding: 6, border: '1px solid #f0f0f0', textAlign: 'center' }}>{t.newTrips}</td>
                    <td style={{ padding: 6, border: '1px solid #f0f0f0', textAlign: 'center' }}>{t.newPublish}</td>
                    <td style={{ padding: 6, border: '1px solid #f0f0f0', textAlign: 'center' }}>{t.newComments}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
};

export default DashboardPage;
