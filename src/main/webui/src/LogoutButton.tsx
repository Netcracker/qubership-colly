import React from "react";
import {Box, IconButton, Typography} from "@mui/material";
import LogoutIcon from '@mui/icons-material/Logout';

type Props = {
    displayedName?: string;
};

export default function LogoutButton({displayedName}: Props) {
    const handleLogout = () => {
        window.location.href = "/q/oidc/logout";
    };

    return (
        <Box sx={{display: 'flex', alignItems: 'center', gap: 2}}>
            <Typography variant="body2" color="text.secondary">
                {displayedName}
            </Typography>
            <IconButton size={"small"} onClick={handleLogout}>
                <LogoutIcon fontSize="inherit"/>
            </IconButton>
        </Box>
    );
};

