import React, {useEffect, useState} from "react";
import {Box} from "@mui/material";
import {DataGrid, GridColDef} from '@mui/x-data-grid';
import LogoutButton from "./LogoutButton";
import {UserInfo} from "../entities/users";
import {Cluster} from "../entities/clusters";


export default function ClustersTable() {
    const [clusters, setClusters] = useState<Cluster[]>([]);
    const [userInfo, setUserInfo] = useState<UserInfo>({authenticated: false});
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        Promise.all([
            fetch("/colly/auth-status").then(res => res.json()),
            fetch("/colly/clusters").then(res => res.json())
        ])
            .then(([authData, clustersData]) => {
                setUserInfo(authData);
                setClusters(clustersData);
            })
            .catch(err => {
                console.error("Failed to fetch data:", err);
                fetch("/colly/clusters")
                    .then(res => res.json())
                    .then(data => setClusters(data))
                    .catch(clustersErr => console.error("Failed to fetch clusters:", clustersErr));
            })
            .finally(() => setLoading(false));
    }, []);

    const columns: GridColDef[] = [
        {
            field: "name",
            headerName: "Name",
            flex: 1
        },
        {
            field: "description",
            headerName: "Description",
            flex: 2
        }
    ];

    const rows = clusters.map(cluster => ({
        id: cluster.name,
        name: cluster.name,
        description: cluster.description || ''
    }));

    if (loading) {
        return <Box sx={{p: 4}}>Loading...</Box>;
    }

    return (
        <Box sx={{p: 4}}>
            {userInfo.authenticated && (
                <Box sx={{display: 'flex', justifyContent: 'flex-end', mb: 2}}>
                    <LogoutButton displayedName={userInfo.username}/>
                </Box>
            )}
            <Box>
                <DataGrid
                    rows={rows}
                    columns={columns}
                    disableRowSelectionOnClick
                    showToolbar
                />
            </Box>
        </Box>
    );
}
