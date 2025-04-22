import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl, InputLabel,
    MenuItem,
    Select,
    TextField
} from "@mui/material";
import React from "react";
import {ALL_STATUSES, Environment, EnvironmentStatus, STATUS_MAPPING} from "../entities/environments";

type Props = {
    environment: Environment;
    onClose: () => void;
    onSave: (env: Environment) => void;
};

export default function EditEnvironmentDialog({environment, onClose, onSave}: Props) {

    const [localEnv, setLocalEnv] = React.useState<Environment>(environment);
    const handleSubmit = () => {
        onSave(localEnv);
    };

    return <Dialog open={!!localEnv} onClose={onClose} fullWidth={true} maxWidth="sm">
        <DialogTitle>Edit Environment</DialogTitle>
        <DialogContent>
            <TextField
                label="Name"
                value={localEnv.name || ''}
                disabled
                fullWidth
                margin="dense"/>
            <TextField
                label="Owner"
                value={localEnv.owner || ''}
                onChange={e => setLocalEnv(prevState => ({...prevState, owner: e.target.value}))}
                fullWidth
                margin="dense"
            />
            <FormControl sx={{mt: 1, mb: 1}} fullWidth>
            <InputLabel>Status</InputLabel>
            <Select
                    value={localEnv.status || ''}
                    onChange={e => setLocalEnv(prev => ({...prev, status: e.target.value as EnvironmentStatus}))}
                    fullWidth
                    label="Status"
                    margin="dense"
            >
                {ALL_STATUSES.map(status => <MenuItem key={status} value={status}>{STATUS_MAPPING[status]}</MenuItem>)}
            </Select>
            </FormControl>
            <TextField
                label="Description"
                value={localEnv.description || ''}
                onChange={e => setLocalEnv(prev => ({...prev, description: e.target.value}))}
                fullWidth
                margin="dense"
            />
        </DialogContent>
        <DialogActions>
            <Button onClick={onClose} color="secondary">Close</Button>
            <Button onClick={handleSubmit} color="primary">Save Changes</Button>
        </DialogActions>
    </Dialog>
}
